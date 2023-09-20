// The file must be named Native<MODULE_NAME>, with a .js or .jsx extension when using Flow,
// or a .ts, or .tsx extension when using TypeScript. Codegen will only look for files matching this pattern.

import type {TurboModule} from 'react-native';
//import type {TurboModule} from 'react-native/Libraries/TurboModule/RCTExport'; //use only for old versions
import {TurboModuleRegistry} from 'react-native';
//import type { Int32 } from 'react-native/Libraries/Types/CodegenTypes';

// Audio Set options
// export type AudioSetOptions = {
//   /** */
//   AudioSourceAndroid?: number, //MediaRecorder.AudioSource.MIC
//   OutputFormatAndroid?: number, //OutputFormatAndroidType.AAC_ADTS
//   AudioEncoderAndroid?: number, //AudioEncoderAndroidType.AAC
//   AudioSamplingRateAndroid?: number,
//   AudioEncodingBitRateAndroid?: number,
//   AudioChannelsAndroid?: number
//   AVEncoderAudioQualityKeyIOS?: number, //AVEncoderAudioQualityIOSType.high
//   AVNumberOfChannelsKeyIOS: number, //2
//   AVFormatIDKeyIOS: number, //AVEncodingOption.aac
//
//   AudioSourceAndroid?: Int32, //MediaRecorder.AudioSource.MIC
//   OutputFormatAndroid?: Int32, //OutputFormatAndroidType.AAC_ADTS
//   AudioEncoderAndroid?: Int32, //AudioEncoderAndroidType.AAC
//   AudioSamplingRateAndroid?: Int32,
//   AudioEncodingBitRateAndroid?: Int32,
//   AudioChannelsAndroid?: Int32
//   AVEncoderAudioQualityKeyIOS?: Int32, //AVEncoderAudioQualityIOSType.high
//   AVNumberOfChannelsKeyIOS: Int32, //2
//   AVFormatIDKeyIOS: Int32, //AVEncodingOption.aac
// }

export interface Spec extends TurboModule {
  // getHelloWorld(first: string, second: string): string;
  startRecorder(path: string, audioSet: Object,meteringEnabled: boolean): Promise<string>;
  resumeRecorder(): Promise<string>;
  pauseRecorder(): Promise<string>;
  stopRecorder(): Promise<string>;
  setVolume(volume: Double): Promise<string>;
  startPlayer(path: string, httpHeaders: Object): Promise<string>;
  resumePlayer(): Promise<string>;
  pausePlayer(): Promise<string>;
  seekToPlayer(time: Double): Promise<string>;
  stopPlayer(): Promise<string>;
  setSubscriptionDuration(sec: Double): Promise<string>;
  // add(a: number, b: number): Promise<number>;
}

// export default TurboModuleRegistry.getEnforcing<Spec>('NativeSampleModule', );
export default TurboModuleRegistry.get<Spec>('NativeARPModule',  ) as Spec | null;
//export default TurboModuleRegistry.getEnforcing<Spec>('NativeAudioRecorderPlayerModule', );
