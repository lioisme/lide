// luaopen_live2d: Lua -> C direct binding for the delua built-in Live2D V2
// native engine (liblive2d.so).
//
// Design (Lua -> C, no Java engine classes in the path):
//   - require "live2d" resolves through package.cpath
//     (nativeLibraryDir/liblive2d.so -> luaopen_live2d), exactly like
//     require "gl" -> libgl.so. All model logic runs in C++.
//   - The Java side is only the Android view widget (GLSurfaceView created
//     by the Lua script) that provides the GL context; per-frame Lua calls
//     live2d.update()/live2d.draw() directly into the native engine.
//   - GL requirements: model load/update/draw MUST run with a current GL
//     context (shaders/textures are created lazily on the GL thread).
//
// API (model userdata):
//   live2d.new()                         -> userdata
//   live2d.free(m)                       (explicit only, no __gc)
//   live2d.load(m, modelJsonPath)        -> bool, err    (GL ctx required)
//   live2d.update(m)
//   live2d.draw(m, mvp)                  (mvp: table of 16 numbers, col-major)
//   live2d.resize(m, w, h)               (GL surface size; must be called
//                                         before drag so screen->scene
//                                         conversion knows the canvas size)
//   live2d.drag(m, x, y)                 (screen pixel coords -> smooth drag)
//   live2d.setDrag(m, x, y)              (scene coords, direct)
//   live2d.setAccel(m, x, y, z)
//   live2d.setAutoBreath(m, bool)
//   live2d.setAutoBlink(m, bool)
//   live2d.isMotionFinished(m)           -> bool
//   live2d.startMotion(m, group, no, priority)   -> bool
//   live2d.startRandomMotion(m, group, priority) -> bool
//   live2d.setExpression(m, name)
//   live2d.setRandomExpression(m)
//   live2d.hitTest(m, areaName, x, y)    -> bool   (canvas-space coords)
//   live2d.getCanvasSize(m)              -> w, h
//   live2d.setupBg(path)                 -> bool, err  (bg texture+program, once)
//   live2d.drawBg(mvp)                   (glClear + fullscreen bg quad, per frame)
//   live2d.nowMs()                       -> wall-clock ms (no Java in hot loop)
//
// Background (plan §5.1 方案 B): the 看板娘 bg quad + clear must not go through
// the LuaJavaAPI proxy per frame (JavaObject leak + uncallable cached refs,
// see PITFALLS "draw 循环零 Java 调用"), so they live here in C, same as the
// model draw path. stb_image decodes the PNG; no BitmapFactory involved.

// delua's bundled Lua headers (build-scripts/jni/lua) are C headers without
// extern "C" guards; compiled from C++ they would mangle every lua_* symbol.
extern "C" {
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
}

#include <string>
#include <cstring>
#include <cmath>
#include <fstream>
#include <vector>

#include <GLES2/gl2.h>
#include <android/log.h>
#include <stb_image.h>

#include "LAppModel.hpp"
#include "Util/UtSystem.hpp"

using namespace live2d;

namespace {

const char* MT_NAME = "live2d.model";

LAppModel* checkModel(lua_State* L, int idx) {
    return *static_cast<LAppModel**>(luaL_checkudata(L, idx, MT_NAME));
}

LAppModel** newModelUserdata(lua_State* L) {
    LAppModel** ud = static_cast<LAppModel**>(lua_newuserdata(L, sizeof(LAppModel*)));
    *ud = nullptr;
    luaL_getmetatable(L, MT_NAME);
    lua_setmetatable(L, -2);
    return ud;
}

int lua_gc(lua_State* L) {
    // 仅由显式 live2d.free 调用（Lua 侧在 GL 线程 reload 路径触发）。
    // 不得注册为 __gc —— 见 luaopen_live2d 内注释（GL 线程亲和性）。
    LAppModel** ud = static_cast<LAppModel**>(luaL_checkudata(L, 1, MT_NAME));
    if (ud && *ud) {
        delete *ud;
        *ud = nullptr;
    }
    return 0;
}

int lua_new(lua_State* L) {
    try {
        LAppModel** ud = newModelUserdata(L);
        *ud = new LAppModel();
        return 1;
    } catch (...) {
        return luaL_error(L, "live2d.new: out of memory");
    }
}

int lua_free(lua_State* L) {
    return lua_gc(L);
}

int lua_load(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    const char* path = luaL_checkstring(L, 2);
    if (!m || !path) { lua_pushboolean(L, 0); lua_pushstring(L, "bad args"); return 2; }
    try {
        if (!m->loadModelJson(path)) {
            lua_pushboolean(L, 0);
            lua_pushstring(L, "model load failed (file/moc/texture error)");
            return 2;
        }
// Lua path: engine owns breath/blink; drag smoothing is driven by Lua's
    // TargetPoint via lua_setDrag per frame (model update no longer overrides).
        m->setAutoBreathEnable(true);
        m->setAutoBlinkEnable(true);
        lua_pushboolean(L, 1);
        lua_pushnil(L);
        return 2;
    } catch (...) {
        lua_pushboolean(L, 0);
        lua_pushstring(L, "live2d.load exception");
        return 2;
    }
}

int lua_update(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) {
        try { m->update(); } catch (...) {}
    }
    return 0;
}

int lua_draw(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    float mvp[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    if (lua_istable(L, 2)) {
        for (int i = 0; i < 16 && i < (int)lua_rawlen(L, 2); i++) {
            lua_rawgeti(L, 2, i + 1);
            if (lua_isnumber(L, -1)) mvp[i] = (float)lua_tonumber(L, -1);
            lua_pop(L, 1);
        }
    }
    if (m) {
        try { m->drawWithMatrix(mvp); } catch (...) {}
    }
    return 0;
}

int lua_setDrag(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) m->setDrag((float)luaL_checknumber(L, 2), (float)luaL_checknumber(L, 3));
    return 0;
}

int lua_resize(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) {
        try {
            m->resize((int)luaL_checkinteger(L, 2), (int)luaL_checkinteger(L, 3));
        } catch (...) {}
    }
    return 0;
}

int lua_drag(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) {
        try {
            m->drag((float)luaL_checknumber(L, 2), (float)luaL_checknumber(L, 3));
        } catch (...) {}
    }
    return 0;
}

int lua_setAccel(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) m->setAccel((float)luaL_checknumber(L, 2), (float)luaL_checknumber(L, 3), (float)luaL_checknumber(L, 4));
    return 0;
}

int lua_setAutoBreath(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) m->setAutoBreathEnable(lua_toboolean(L, 2));
    return 0;
}

int lua_setAutoBlink(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) m->setAutoBlinkEnable(lua_toboolean(L, 2));
    return 0;
}

int lua_isMotionFinished(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    lua_pushboolean(L, m && m->isMotionFinished());
    return 1;
}

int lua_startMotion(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    const char* group = luaL_checkstring(L, 2);
    int no = (int)luaL_checkinteger(L, 3);
    int prio = (int)luaL_optinteger(L, 4, 1);
    if (!m || !group) { lua_pushboolean(L, 0); return 1; }
    try { m->startMotion(group, no, prio); } catch (...) { lua_pushboolean(L, 0); return 1; }
    lua_pushboolean(L, 1);
    return 1;
}

int lua_startRandomMotion(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    const char* group = luaL_optstring(L, 2, "");
    int prio = (int)luaL_optinteger(L, 3, 1);
    if (!m) { lua_pushboolean(L, 0); return 1; }
    try { m->startRandomMotion(group ? group : "", prio); } catch (...) { lua_pushboolean(L, 0); return 1; }
    lua_pushboolean(L, 1);
    return 1;
}

int lua_setExpression(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    const char* name = luaL_checkstring(L, 2);
    if (m && name) {
        try { m->setExpression(name); } catch (...) {}
    }
    return 0;
}

int lua_setRandomExpression(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) {
        try { m->setRandomExpression(); } catch (...) {}
    }
    return 0;
}

int lua_hitTest(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    const char* area = luaL_checkstring(L, 2);
    float x = (float)luaL_checknumber(L, 3);
    float y = (float)luaL_checknumber(L, 4);
    bool hit = false;
    if (m && area) {
        try { hit = m->hitTestArea(area, x, y); } catch (...) {}
    }
    lua_pushboolean(L, hit);
    return 1;
}

int lua_getCanvasSize(lua_State* L) {
    LAppModel* m = checkModel(L, 1);
    if (m) {
        lua_pushnumber(L, (double)m->getCanvasWidth());
        lua_pushnumber(L, (double)m->getCanvasHeight());
    } else {
        lua_pushnumber(L, 0);
        lua_pushnumber(L, 0);
    }
    return 2;
}

// ==================== background (方案 B: native, zero Java proxy) ====================

struct BgState {
    bool ready = false;
    GLuint tex = 0;
    GLuint prog = 0;
    GLuint vbo = 0;
    GLint aPos = -1;
    GLint aUV = -1;
    GLint uMVP = -1;
};

static BgState gBg;

static bool readAll(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    auto sz = f.tellg();
    f.seekg(0);
    out.resize((size_t)sz);
    f.read((char*)out.data(), sz);
    return !out.empty();
}

static GLuint compileShader(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    if (!sh) return 0;
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

int lua_setupBg(lua_State* L) {
    const char* path = luaL_checkstring(L, 1);
    if (!path) { lua_pushboolean(L, 0); lua_pushstring(L, "bad args"); return 2; }
    try {
        // Texture (same upload convention as LAppModel: stbi top-down rows,
        // no flip — matches the Lua BitmapFactory + GLUtils.texImage2D path)
        std::vector<uint8_t> fileData;
        if (!readAll(path, fileData)) {
            lua_pushboolean(L, 0); lua_pushstring(L, "bg file read failed"); return 2;
        }
        int w = 0, h = 0, n = 0;
        unsigned char* pixels = stbi_load_from_memory(fileData.data(), (int)fileData.size(), &w, &h, &n, 4);
        if (!pixels) {
            lua_pushboolean(L, 0); lua_pushstring(L, "bg stbi decode failed"); return 2;
        }
        GLuint tex = 0;
        glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        stbi_image_free(pixels);

        // Program (same sources as the Lua-side quad shader)
        const char* vsSrc =
            "attribute vec4 aPos;attribute vec2 aUV;uniform mat4 uMVP;"
            "varying vec2 vUV;void main(){gl_Position=uMVP*aPos;vUV=aUV;}";
        const char* fsSrc =
            "precision mediump float;varying vec2 vUV;uniform sampler2D uTex;"
            "void main(){gl_FragColor=texture2D(uTex,vUV);}";
        GLuint vs = compileShader(GL_VERTEX_SHADER, vsSrc);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, fsSrc);
        if (!vs || !fs) {
            if (vs) glDeleteShader(vs);
            if (fs) glDeleteShader(fs);
            glDeleteTextures(1, &tex);
            lua_pushboolean(L, 0); lua_pushstring(L, "bg shader compile failed"); return 2;
        }
        GLuint prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        glDeleteShader(vs);
        glDeleteShader(fs);
        GLint ok = 0;
        glGetProgramiv(prog, GL_LINK_STATUS, &ok);
        if (!ok) {
            glDeleteProgram(prog);
            glDeleteTextures(1, &tex);
            lua_pushboolean(L, 0); lua_pushstring(L, "bg link failed"); return 2;
        }

        // VBO: interleaved [pos2 uv2] x4, TRIANGLE_STRIP (-2..2 over-scan quad,
        // matches setDrawRect(-2,2,-2,2)+setUVRect(0,1,0,1))
        const float verts[4][4] = {
            {-2, -2, 0, 0}, {2, -2, 1, 0}, {-2, 2, 0, 1}, {2, 2, 1, 1},
        };
        GLuint vbo = 0;
        glGenBuffers(1, &vbo);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        BgState st;
        st.ready = true;
        st.tex = tex;
        st.prog = prog;
        st.vbo = vbo;
        st.aPos = glGetAttribLocation(prog, "aPos");
        st.aUV = glGetAttribLocation(prog, "aUV");
        st.uMVP = glGetUniformLocation(prog, "uMVP");
        gBg = st;
        lua_pushboolean(L, 1);
        lua_pushnil(L);
        return 2;
    } catch (...) {
        lua_pushboolean(L, 0);
        lua_pushstring(L, "setupBg exception");
        return 2;
    }
}

int lua_drawBg(lua_State* L) {
    // glClear always (dex onDrawFrame @020dcc: glClear(0x4000) first)
    glClear(GL_COLOR_BUFFER_BIT);
    if (!gBg.ready) return 0;
    float mvp[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    if (lua_istable(L, 1)) {
        for (int i = 0; i < 16 && i < (int)lua_rawlen(L, 1); i++) {
            lua_rawgeti(L, 1, i + 1);
            if (lua_isnumber(L, -1)) mvp[i] = (float)lua_tonumber(L, -1);
            lua_pop(L, 1);
        }
    }
    try {
        glUseProgram(gBg.prog);
        glUniformMatrix4fv(gBg.uMVP, 1, GL_FALSE, mvp);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, gBg.tex);
        glEnable(GL_BLEND);
        // 用 glBlendFuncSeparate 代替 glBlendFunc（语义完全等价），规避本 ROM/模拟器
        // GLESv2 层 glBlendFunc 符号 GOT 偶发指向未映射区域（17:36/17:46 双 tombstone
        // 实证：仅此符号崩溃；模型路径每帧调用 glBlendFuncSeparate 从未崩溃）
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glBindBuffer(GL_ARRAY_BUFFER, gBg.vbo);
        glEnableVertexAttribArray((GLuint)gBg.aPos);
        glEnableVertexAttribArray((GLuint)gBg.aUV);
        glVertexAttribPointer((GLuint)gBg.aPos, 2, GL_FLOAT, GL_FALSE, 16, nullptr);
        glVertexAttribPointer((GLuint)gBg.aUV, 2, GL_FLOAT, GL_FALSE, 16, (const void*)8);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray((GLuint)gBg.aPos);
        glDisableVertexAttribArray((GLuint)gBg.aUV);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    } catch (...) {}
    return 0;
}

int lua_nowMs(lua_State* L) {
    lua_pushnumber(L, UtSystem::getUserTimeMSec());
    return 1;
}

int lua_log(lua_State* L) {
    // GL 线程零 Java 红线：draw 循环 / GL 回调里的 Lua print 会经 luajava 回调 Java
    // （Toast+Handler），每秒一次即构成从 native GL 线程的 JNI 调用（tombstone_12
    // GLThread 崩溃实证）。本函数把输出直接写到 logcat，不走 Java。主线程原 print
    // 语义（Toast+logcat）不变，仅 native 分支内 print 被 main.lua 重定向到此处。
    const char* s = luaL_optstring(L, 1, "");
    __android_log_print(ANDROID_LOG_INFO, "lua", "%s", s);
    return 0;
}

int lua_glError(lua_State* L) {
    // 替代 onDrawFrame 里 GLES20.glGetError()（Java 静态方法，仍是 GL 线程上的
    // JNI 调用）；本函数纯 C，无 Java 边界。
    lua_pushnumber(L, (lua_Number)glGetError());
    return 1;
}

const luaL_Reg kFuncs[] = {
    {"new", lua_new},
    {"free", lua_free},
    {"load", lua_load},
    {"update", lua_update},
    {"draw", lua_draw},
    {"resize", lua_resize},
    {"drag", lua_drag},
    {"setDrag", lua_setDrag},
    {"setAccel", lua_setAccel},
    {"setAutoBreath", lua_setAutoBreath},
    {"setAutoBlink", lua_setAutoBlink},
    {"isMotionFinished", lua_isMotionFinished},
    {"startMotion", lua_startMotion},
    {"startRandomMotion", lua_startRandomMotion},
    {"setExpression", lua_setExpression},
    {"setRandomExpression", lua_setRandomExpression},
    {"hitTest", lua_hitTest},
    {"getCanvasSize", lua_getCanvasSize},
    {"setupBg", lua_setupBg},
    {"drawBg", lua_drawBg},
    {"nowMs", lua_nowMs},
    {"log", lua_log},
    {"glError", lua_glError},
    {nullptr, nullptr},
};

const luaL_Reg kMethods[] = {
    {"free", lua_free},
    {"load", lua_load},
    {"update", lua_update},
    {"draw", lua_draw},
    {"resize", lua_resize},
    {"drag", lua_drag},
    {"setDrag", lua_setDrag},
    {"setAccel", lua_setAccel},
    {"setAutoBreath", lua_setAutoBreath},
    {"setAutoBlink", lua_setAutoBlink},
    {"isMotionFinished", lua_isMotionFinished},
    {"startMotion", lua_startMotion},
    {"startRandomMotion", lua_startRandomMotion},
    {"setExpression", lua_setExpression},
    {"setRandomExpression", lua_setRandomExpression},
    {"hitTest", lua_hitTest},
    {"getCanvasSize", lua_getCanvasSize},
    {nullptr, nullptr},
};

} // namespace

extern "C" {

int luaopen_live2d(lua_State* L) {
    luaL_newmetatable(L, MT_NAME);
    // 不注册 __gc：模型持有 GL 对象（program/texture，GL 线程创建），若由 Lua GC
    // （主线程）回收会在无 GL 上下文的线程上 delete → 模拟器 libOpenglCodecCommon
    // 崩溃（17:50 tombstone 实证）。删除只走显式 live2d.free（Lua 侧仅在 GL 线程
    // 的 reload 路径调用），进程退出时由系统回收。
    lua_newtable(L);
    luaL_setfuncs(L, kMethods, 0);
    lua_setfield(L, -2, "__index");
    lua_pop(L, 1);

    lua_newtable(L);
    luaL_setfuncs(L, kFuncs, 0);
    return 1;
}

} // extern "C"
