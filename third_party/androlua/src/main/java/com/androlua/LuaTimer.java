package com.androlua;

import com.androlua.util.*;
import com.luajava.*;

public class LuaTimer extends TimerX implements LuaGcable
{

	private boolean mGc;

	@Override
	public void gc() {
		// TODO: Implement this method
		stop();
		mGc=true;
	}

	@Override
	public boolean isGc() {
		return mGc;
	}

	private LuaTimerTask task;
	
	public LuaTimer(LuaContext main,String src) throws LuaException
	{
		this(main,src,null);
	}
	public LuaTimer(LuaContext main,String src,Object[] arg) throws LuaException
	{
		super("LuaTimer");
		main.regGc(this);
		task= new LuaTimerTask(main, src,arg);
	}
	
	public LuaTimer(LuaContext main,LuaObject func) throws LuaException
	{
		this(main,func,null);
	}
	public LuaTimer(LuaContext main,LuaObject func,Object[] arg) throws LuaException
	{
		super("LuaTimer");
		main.regGc(this);
		task= new LuaTimerTask(main, func, arg);
	}
	
	public void start(long delay, long period)
	{
		schedule(task,delay,period);
	}
	
	public void start(long delay)
	{
		schedule(task,delay);
	}
	
	public void stop()
	{
		task.cancel();
		// 资源收口（内存泄漏修复）：原实现仅 task.cancel()，LuaTimerTask 的独立
		// LuaState 从不关闭，每个定时器泄漏一个原生 lua_State，且 Timer 线程（非守护）
		// 永不退出。这里在 Timer 线程排一个一次性收口任务：Timer 队列串行执行，
		// 保证收口发生在在途 run() 之后（close 与 run 无并发）；close 幂等，
		// 若 run() 的 finally 已关闭则此处为 no-op。由收口任务自身 cancel 定时器
		// （先 cancel 会丢弃队列，导致收口任务无法执行）。
		try
		{
			final LuaTimerTask t = task;
			schedule(new TimerTaskX() {
				@Override
				public boolean isEnabled()
				{
					// TimerImpl 只执行 isEnabled() 的任务；TimerTaskX 默认 false，
					// 不覆写则收口任务永不执行（线程与 LuaState 泄漏）。
					return true;
				}
				@Override
				public void run()
				{
					t.closeLua();
					// 注意：此处必须显式调用外层 LuaTimer.cancel() 停止定时器线程；
					// 裸写 cancel() 会解析为 TimerTaskX.cancel()（仅取消收口任务自身），
					// 导致非守护 Timer 线程永不退出。
					LuaTimer.this.cancel();
				}
			}, 0);
		}
		catch (IllegalStateException ignored)
		{
			// timer 已 cancel（重复 stop 或任务执行中异常触发 TimerImpl 取消）：
			// Timer 线程已退出或即将退出，无在途 run，直接关闭安全
			task.closeLua();
		}
	}
	
	public void setEnabled(boolean enabled)
	{
		task.setEnabled(enabled);
	}

	public boolean isEnabled()
	{
		return task.isEnabled();
	}
	
	public boolean getEnabled()
	{
		return task.isEnabled();
	}
	
	public void setPeriod(long period)
	{
		task.setPeriod(period);
	}

	public long getPeriod()
	{
		return task.getPeriod();
	}
}
