#pragma once
#include "Framework/L2DBaseModel.hpp"
#include "Framework/L2DTargetPoint.hpp"
#include "Framework/MatrixManager.hpp"
#include "Model/Live2DModelOpenGL.hpp"
#include <functional>
#include <string>


namespace live2d {
class LAppModel : public L2DBaseModel {
public:
    using StartCallback = std::function<void(const std::string&, int)>;
    using FinishCallback = std::function<void(const std::string&, int)>;

    LAppModel();
    ~LAppModel() override;
    bool loadModelJson(const std::string& path);
    void resize(int w, int h);
    void drag(float x, float y);
    void touch(float x, float y);
    bool isMotionFinished() const;
    void setOffset(float dx, float dy);
    void setScale(float s);
    void setParameterValue(const std::string& id, float val, float weight = 1.0f);
    void addParameterValue(const std::string& id, float val, float weight = 1.0f);
    void setAutoBreathEnable(bool v) { mAutoBreath = v; }
    void setAutoBlinkEnable(bool v) { mAutoBlink = v; }
    int getParameterCount() const;
    int getPartCount() const;
    std::string getPartId(int index) const;
    void setPartOpacity(int index, float val);
    void update();
    void draw();
    // Bridge extensions: external MVP draw (Java owns all matrix math)
    void drawWithMatrix(const float mvp[16]);
    // Bridge extensions: hit test with canvas-space coords (Java transforms)
    bool hitTestCanvas(const std::string& drawID, float x, float y);
    // Lua-path: hit test by model json hit_areas name (e.g. "head"/"body")
    bool hitTestArea(const std::string& areaName, float x, float y);
    bool hitTest(const std::string& area, float x, float y);
    void setExpression(const std::string& name);
    void setRandomExpression();
    void startMotion(const std::string& group, int no, int priority,
                     StartCallback onStart = nullptr, FinishCallback onFinish = nullptr);
    void startRandomMotion(const std::string& group, int priority,
                           StartCallback onStart = nullptr, FinishCallback onFinish = nullptr);
    void clearMotions();
    void stopAllMotions() { clearMotions(); }
    void resetExpression();
    void resetPose();
    float getCanvasWidth() const { return (float)mLive2DModel->getCanvasWidth(); }
    float getCanvasHeight() const { return (float)mLive2DModel->getCanvasHeight(); }
    int getPixelsPerUnit() const { return 1; }
    void rotate(float deg);
    float getParameterValue(int index) const;
    float getParameterMin(int index) const;
    float getParameterMax(int index) const;
    float getParameterDefault(int index) const;
    std::string getParameterId(int index) const;
    void setPartScreenColor(int index, float r, float g, float b, float a);
    void setPartMultiplyColor(int index, float r, float g, float b, float a);
    std::vector<float> getPartScreenColor(int index) const;
    std::vector<float> getPartMultiplyColor(int index) const;
    std::vector<std::string> hitPart(float x, float y, bool topOnly);
    void setTexture(int no, int texId);
    MatrixManager mMatrixManager;
    bool mAutoBreath = true, mAutoBlink = true;
    bool mClearFlag = false;
    std::string mModelHomeDir;
    // hit_areas from model json: {name, drawID}
    std::vector<std::pair<std::string, std::string>> mHitAreas;
private:
    StartCallback mOnStartMotion;
    FinishCallback mOnFinishMotion;
    bool mCallbacksPending = false;
    std::string mCurrentGroup; int mCurrentNo = 0;
};
} // namespace live2d
