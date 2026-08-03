package com.androlua;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;

import com.luajava.LuaException;

import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class LuaDexLoader {
    private static HashMap<String, ClassLoader> dexCache = new HashMap<String, ClassLoader>();
    private ArrayList<ClassLoader> dexList = new ArrayList<ClassLoader>();
    private HashMap<String, String> libCache = new HashMap<String, String>();

    private LuaContext mContext;

    private String luaDir;

    private AssetManager mAssetManager;

    private LuaResources mResources;
    private Resources.Theme mTheme;
    private String odexDir;

    public LuaDexLoader(LuaContext context) {
        mContext = context;
        luaDir = context.getLuaDir();
        LuaApplication app = LuaApplication.getInstance();
        //localDir = app.getLocalDir();
        odexDir = app.getOdexDir();
    }

    public Resources.Theme getTheme() {
        return mTheme;
    }

    public ArrayList<ClassLoader> getClassLoaders() {
        // TODO: Implement this method
        return dexList;
    }

    public ClassLoader loadApp(String pkg) {
        try {
            ClassLoader dex = dexCache.get(pkg);
            if (dex == null) {
                PackageManager manager = mContext.getContext().getPackageManager();
                ApplicationInfo info = manager.getPackageInfo(pkg, 0).applicationInfo;
                dex = new LuaDexClassLoader(info.publicSourceDir, LuaApplication.getInstance().getOdexDir(), info.nativeLibraryDir, mContext.getContext().getClassLoader());
                dexCache.put(pkg, dex);
            }
            if (!dexList.contains(dex)) {
                dexList.add(dex);
            }
            return dex;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void loadLibs() throws LuaException {
        File[] libs = new File(mContext.getLuaDir() + "/libs").listFiles();
        if (libs == null)
            return;
        for (File f : libs) {
            if(f.isDirectory())
                continue;
            if (f.getAbsolutePath().endsWith(".so"))
                loadLib(f.getName());
            else
                loadDex(f.getAbsolutePath());
        }
    }

    public void loadLib(String name) throws LuaException {
        String fn = name;
        int i = name.indexOf(".");
        if (i > 0)
            fn = name.substring(0, i);
        if (fn.startsWith("lib"))
            fn = fn.substring(3);
        String libDir = mContext.getContext().getDir(fn, Context.MODE_PRIVATE).getAbsolutePath();
        String libPath = libDir + "/lib" + fn + ".so";
        File f = new File(libPath);
        if (!f.exists()) {
            f = new File(luaDir + "/libs/lib" + fn + ".so");
            if (!f.exists())
                throw new LuaException("can not find lib " + name);
            LuaUtil.copyFile(luaDir + "/libs/lib" + fn + ".so", libPath);

        }
        libCache.put(fn, libPath);
    }

    public HashMap<String, String> getLibrarys() {
        return libCache;
    }


    public ClassLoader loadDex(String path) throws LuaException {
        ClassLoader dex = dexCache.get(path);
        if(dex==null)
           dex = loadApp(path);
        if (dex == null){
            String name = path;
            if (path.charAt(0) != '/')
                path = luaDir + "/" + path;
            if (!new File(path).exists())
                if (new File(path + ".dex").exists())
                    path += ".dex";
                else if (new File(path + ".jar").exists())
                    path += ".jar";
                else
                    throw new LuaException(path + " not found");
            String id = LuaUtil.getFileMD5(path);
            if (id != null && id.equals("0"))
                id = name;
            dex = dexCache.get(id);

            if (dex == null) {
                // Android 8.0+（W^X 策略）禁止从 app 可写位置加载 dex：
                // /sdcard 经 FUSE 呈现 0664（chmod 无效）、/data/data 亦被拒，
                // 均抛 SecurityException "Writable dex file ... is not allowed"。
                // API 26+ 改走内存加载绕过路径校验；API < 26 保持原文件加载。
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    try {
                        FileInputStream in = new FileInputStream(path);
                        byte[] data;
                        try {
                            data = LuaUtil.readAll(in);
                        } finally {
                            in.close();
                        }
                        dex = new InMemoryDexClassLoader(ByteBuffer.wrap(data), mContext.getContext().getClassLoader());
                    } catch (IOException e) {
                        throw new LuaException(path + " can not load dex: " + e.getMessage());
                    }
                } else {
                    dex = new LuaDexClassLoader(path, odexDir, LuaApplication.getInstance().getApplicationInfo().nativeLibraryDir, mContext.getContext().getClassLoader());
                }
                dexCache.put(id, dex);
            }
        }

        if (!dexList.contains(dex)) {
            dexList.add(dex);
            if (dex instanceof LuaDexClassLoader) {
                path = ((LuaDexClassLoader) dex).getDexPath();
                if (path.endsWith(".jar"))
                    loadResources(path);
            }
        }
        return dex;
    }

    public void loadResources(String path) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            int ok = (int) addAssetPath.invoke(assetManager, path);
            if (ok == 0)
                return;
            mAssetManager = assetManager;
            Resources superRes = mContext.getContext().getResources();
            mResources = new LuaResources(mAssetManager, superRes.getDisplayMetrics(),
                    superRes.getConfiguration());
            mResources.setSuperResources(superRes);
            mTheme = mResources.newTheme();
            mTheme.setTo(mContext.getContext().getTheme());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AssetManager getAssets() {
        return mAssetManager;
    }

    public Resources getResources() {
        return mResources;
    }


}
