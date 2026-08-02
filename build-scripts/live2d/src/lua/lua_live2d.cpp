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
//   live2d.free(m)                       (also via __gc)
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

#include "LAppModel.hpp"

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
        // Lua path: engine owns breath/blink and drag smoothing
        // (update() drives L2DTargetPoint internally).
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
    lua_pushcfunction(L, lua_gc);
    lua_setfield(L, -2, "__gc");
    lua_newtable(L);
    luaL_setfuncs(L, kMethods, 0);
    lua_setfield(L, -2, "__index");
    lua_pop(L, 1);

    lua_newtable(L);
    luaL_setfuncs(L, kFuncs, 0);
    return 1;
}

} // extern "C"
