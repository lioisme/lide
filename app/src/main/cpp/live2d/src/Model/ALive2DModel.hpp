#pragma once

#include <memory>
#include "../Core/ModelImpl.hpp"

namespace live2d {

class ModelContext;
class DrawParamOpenGL;

class ALive2DModel {
public:
    ALive2DModel();
    virtual ~ALive2DModel();

    void setModelImpl(ModelImpl* impl) { mModelImpl = impl; }
    ModelImpl* getModelImpl();
    ModelContext* getModelContext() const { return mModelContext.get(); }
    virtual DrawParamOpenGL* getDrawParam() = 0;
    virtual void draw() = 0;

    int getCanvasWidth() const;
    int getCanvasHeight() const;
    float getParamFloat(int index) const;
    void setParamFloat(int index, float value, float weight = 1.0f);

protected:
    // 非拥有指针：对象图由 L2DBaseModel::mMocReader（BinaryReader）全权拥有
    // （见 BinaryReader::readKnownTypeObject 注释）。只引用，不 delete。
    ModelImpl* mModelImpl = nullptr;
    std::unique_ptr<ModelContext> mModelContext;
};

} // namespace live2d
