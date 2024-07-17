// Copyright (c) 2019 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "Native.h"
#include "pipeline.h"
#include <android/log.h>

/*
 * Class:     Java_com_engine_scan_ppocr_Native
 * Method:    nativeInit
 * Signature: BaTD
*/
extern "C" JNIEXPORT jlong JNICALL
Java_com_engine_scan_ppocr_Native_nativeInit(
    JNIEnv *env, jclass thiz, jstring jDetModelPath, jstring jClsModelPath,
    jstring jRecModelPath, jstring jConfigPath, jstring jLabelPath,
    jint cpuThreadNum, jstring jCPUPowerMode) {
  std::string detModelPath = jstring_to_cpp_string(env, jDetModelPath);
  std::string clsModelPath = jstring_to_cpp_string(env, jClsModelPath);
  std::string recModelPath = jstring_to_cpp_string(env, jRecModelPath);
  std::string configPath = jstring_to_cpp_string(env, jConfigPath);
  std::string labelPath = jstring_to_cpp_string(env, jLabelPath);
  std::string cpuPowerMode = jstring_to_cpp_string(env, jCPUPowerMode);

  return reinterpret_cast<jlong>(
      new Pipeline(detModelPath, clsModelPath, recModelPath, cpuPowerMode,
                   cpuThreadNum, configPath, labelPath));
}

/*
 * Class:     Java_com_engine_scan_ppocr_Native
 * Method:    nativeRelease
 * Signature: BaTD
 */
JNIEXPORT jboolean JNICALL
Java_com_engine_scan_ppocr_Native_nativeRelease(
        JNIEnv *env,
        jclass thiz,
        jlong ctx)
  {
      if (ctx == 0) {
        return JNI_FALSE;
      }
      auto *pipeline = reinterpret_cast<Pipeline *>(ctx);
      delete pipeline;
      return JNI_TRUE;
}

/*
 * Class:     Java_com_engine_scan_ppocr_Native
 * Method:    nativeProcess
 * Signature: BaTD
 */
JNIEXPORT jboolean JNICALL
Java_com_engine_scan_ppocr_Native_nativeProcess(
    JNIEnv *env, jclass thiz, jlong ctx, jint inTextureId, jint outTextureId,
    jint textureWidth, jint textureHeight, jstring jsavedImagePath) {
  if (ctx == 0) {
    return JNI_FALSE;
  }
  std::string savedImagePath = jstring_to_cpp_string(env, jsavedImagePath);
  auto *pipeline = reinterpret_cast<Pipeline *>(ctx);
  return pipeline->Process_val(inTextureId, outTextureId, textureWidth,
                               textureHeight, savedImagePath);
}