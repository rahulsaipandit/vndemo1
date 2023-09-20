package com.rtnaudiolib

import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider
//import com.facebook.react.uimanager.ViewManager
import java.util.*

// RPTODO
import com.facebook.react.TurboReactPackage;
import com.facebook.react.module.model.ReactModuleInfo
//import androidx.annotation.Nullable;
//import java.util.Collections;
//import java.util.List;
//import java.util.HashMap;
//import java.util.Map;
//RPTODO

class NativeARPPackage : TurboReactPackage() {
    override fun getModule(name: String?, reactContext: ReactApplicationContext): NativeModule? =
       if (name == NativeARPModule.NAME) {
        NativeARPModule(reactContext)
       } else {
         null
       }

     override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
       mapOf(
         NativeARPModule.NAME to ReactModuleInfo(
           NativeARPModule.NAME,
           NativeARPModule.NAME,
           false, // canOverrideExistingModule
           false, // needsEagerInit
           true, // hasConstants
           false, // isCxxModule
           true // isTurboModule
         )
       )
     }
}
