#include "L2DBaseModel.hpp"
#include "../Model/Live2DModelOpenGL.hpp"
#include "../Motion/AMotion.hpp"
#include "../Motion/Live2DMotion.hpp"
#include "L2DEyeBlink.hpp"
#include "L2DPose.hpp"
#include "L2DPhysics.hpp"
#include "L2DExpressionMotion.hpp"
#include "L2DMotionManager.hpp"
#include "../Model/ModelContext.hpp"
#include "../Core/BinaryReader.hpp"
#include "../Core/ModelImpl.hpp"
#include "../Draw/MeshContext.hpp"
#include "Log.hpp"
namespace live2d {
L2DBaseModel::L2DBaseModel()
    : mLive2DModel(std::make_unique<Live2DModelOpenGL>())
    , mEyeBlink(std::make_unique<L2DEyeBlink>())
    , mPhysics(std::make_unique<L2DPhysics>())
    , mPose(std::make_unique<L2DPose>())
    , mMainMotionMgr(std::make_unique<L2DMotionManager>())
    , mExpressionMgr(std::make_unique<L2DMotionManager>()) {}

L2DBaseModel::~L2DBaseModel() = default;

void L2DBaseModel::loadModelData(const std::vector<uint8_t>& data, int version) {
    // 先建新 reader（新对象图）再交接：
    // 旧图必须在 ALive2DModel::mModelImpl 换新指针之后才销毁，
    // 因此 setModelImpl 先执行，随后 mMocReader = move(newReader) 才释放旧图。
    auto newReader = std::make_unique<BinaryReader>(data);
    newReader->readByte(); newReader->readByte(); newReader->readByte();
    version = newReader->readByte();
    newReader->setFormatVersion(version);
    auto* impl = static_cast<ModelImpl*>(newReader->readObjectRaw());
    mLive2DModel->setModelImpl(impl);
    mMocReader = std::move(newReader);
    mLive2DModel->getModelContext()->setDrawParam(mLive2DModel->getDrawParam());
    mLive2DModel->getModelContext()->init();
    mModelMatrix = L2DModelMatrix(
        (float)mLive2DModel->getCanvasWidth(),
        (float)mLive2DModel->getCanvasHeight());
    mModelMatrix.setWidth(2);
    mModelMatrix.setCenterPosition(0, 0);
}
AMotion* L2DBaseModel::loadMotion(const std::string& name, const std::vector<uint8_t>& data) {
    auto* m = Live2DMotion::load(data);
    mMotions[name].emplace_back(m); return m;
}
AMotion* L2DBaseModel::loadExpression(const std::string& name, const std::vector<uint8_t>& data) {
    auto* m = L2DExpressionMotion::load(data);
    mExpressions[name].reset(m); return m;
}
L2DPose* L2DBaseModel::loadPose(const std::vector<uint8_t>& data) {
    mPose.reset(L2DPose::load(data)); return mPose.get();
}
void L2DBaseModel::loadPhysics(const std::vector<uint8_t>& data) {
    mPhysics.reset(L2DPhysics::load(data));
}
bool L2DBaseModel::hitTestSimple(const std::string& drawID, float x, float y) {
    auto* mc = mLive2DModel->getModelContext();
    int drawIdx = mc->getDrawDataIndex(&Id::getID(drawID));
    if (drawIdx < 0) return false;

    auto* dctx = mc->getDrawContext(drawIdx);
    if (!dctx || !dctx->mAvailable) return false;

    auto& verts = !dctx->mTransformedPoints.empty()
                      ? dctx->mTransformedPoints
                      : dctx->mInterpolatedPoints;
    if (verts.empty()) return false;

    float left = (float)mLive2DModel->getCanvasWidth(), right = 0;
    float top = (float)mLive2DModel->getCanvasHeight(), bottom = 0;
    for (size_t j = 0; j < verts.size(); j += 2) {
        float vx = verts[j], vy = verts[j + 1];
        if (vx < left) left = vx;
        if (vx > right) right = vx;
        if (vy < top) top = vy;
        if (vy > bottom) bottom = vy;
    }

    float tx = mModelMatrix.invertTransformX(x);
    float ty = mModelMatrix.invertTransformY(y);
    return left <= tx && tx <= right && top <= ty && ty <= bottom;
}
} // namespace live2d
