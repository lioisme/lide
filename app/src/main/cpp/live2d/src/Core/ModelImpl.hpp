#pragma once
#include <memory>

#include "ISerializable.hpp"
#include <vector>

#include "ParamDefSet.hpp"
#include "PartsData.hpp"

namespace live2d {

class ModelImpl final : public ISerializable {
public:
    ModelImpl() = default;
    ~ModelImpl() override;

    void read(class BinaryReader& br) override;

    int getCanvasWidth() const { return mCanvasWidth; }
    int getCanvasHeight() const { return mCanvasHeight; }

    ParamDefSet* getParamDefSet() const { return mParamDefSet; }
    std::vector<PartsData*>& getPartsDataList() { return mPartsDataList; }

private:
    // 非拥有指针：对象图由 BinaryReader 全权拥有（见 readKnownTypeObject 注释）
    ParamDefSet* mParamDefSet = nullptr;
    std::vector<PartsData*> mPartsDataList;
    int mCanvasWidth = 400;
    int mCanvasHeight = 400;
};

} // namespace live2d
