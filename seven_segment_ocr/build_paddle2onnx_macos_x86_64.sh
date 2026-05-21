#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
P2O_REF="${P2O_REF:-develop}"
WORK_DIR="${P2O_WORK_DIR:-/tmp/paddle2onnx-macos-x86_64-build}"
P2O_SRC="${WORK_DIR}/Paddle2ONNX"
PROTO_SRC="${WORK_DIR}/protobuf-21.12"
PROTO_BUILD="${WORK_DIR}/protobuf-21.12-build"
PROTO_PREFIX="${WORK_DIR}/protobuf-21.12-install"
WHEEL_DIR="${P2O_WHEEL_DIR:-${ROOT_DIR}/.local_wheels}"
JOBS="${MAX_JOBS:-4}"

if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "x86_64" ]]; then
  echo "This helper is for macOS x86_64 only." >&2
  exit 2
fi

command -v git >/dev/null
command -v cmake >/dev/null

mkdir -p "${WORK_DIR}" "${WHEEL_DIR}"

if [[ ! -x "${PROTO_PREFIX}/bin/protoc" ]]; then
  rm -rf "${PROTO_SRC}" "${PROTO_BUILD}" "${PROTO_PREFIX}"
  git clone --depth 1 --branch v21.12 https://github.com/protocolbuffers/protobuf.git "${PROTO_SRC}"
  git -C "${PROTO_SRC}" submodule update --init --recursive
  cmake -S "${PROTO_SRC}/cmake" -B "${PROTO_BUILD}" \
    -DCMAKE_INSTALL_PREFIX="${PROTO_PREFIX}" \
    -Dprotobuf_BUILD_SHARED_LIBS=OFF \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -Dprotobuf_BUILD_TESTS=OFF \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_CXX_STANDARD=14 \
    -DCMAKE_OSX_ARCHITECTURES=x86_64
  cmake --build "${PROTO_BUILD}" --target install -- -j"${JOBS}"
fi

if [[ ! -d "${P2O_SRC}/.git" ]]; then
  git clone --depth 1 --branch "${P2O_REF}" https://github.com/PaddlePaddle/Paddle2ONNX.git "${P2O_SRC}"
else
  git -C "${P2O_SRC}" fetch --depth 1 origin "${P2O_REF}"
  git -C "${P2O_SRC}" checkout FETCH_HEAD
fi

git -C "${P2O_SRC}" submodule update --init third_party/glog third_party/onnx third_party/optimizer third_party/pybind11
rm -rf "${P2O_SRC}/.setuptools-cmake-build" "${P2O_SRC}/build" "${P2O_SRC}/paddle2onnx.egg-info"

(
  cd "${ROOT_DIR}"
  PATH="${PROTO_PREFIX}/bin:${PATH}" \
  CPPFLAGS="-I${PROTO_PREFIX}/include" \
  CXXFLAGS="-I${PROTO_PREFIX}/include" \
  CMAKE_ARGS="-DCMAKE_OSX_ARCHITECTURES=x86_64 -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_CXX_FLAGS=-I${PROTO_PREFIX}/include -DCMAKE_C_FLAGS=-I${PROTO_PREFIX}/include -DCMAKE_PREFIX_PATH=${PROTO_PREFIX} -DProtobuf_ROOT=${PROTO_PREFIX} -DProtobuf_PROTOC_EXECUTABLE=${PROTO_PREFIX}/bin/protoc -DProtobuf_INCLUDE_DIR=${PROTO_PREFIX}/include -DProtobuf_LIBRARY=${PROTO_PREFIX}/lib/libprotobuf.a" \
  MAX_JOBS="${JOBS}" \
  pixi run python -m pip wheel "${P2O_SRC}" --no-build-isolation --no-deps -w "${WHEEL_DIR}" -v
)

echo "Built Paddle2ONNX wheel(s):"
ls -lh "${WHEEL_DIR}"/paddle2onnx-*.whl
