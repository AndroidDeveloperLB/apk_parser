package com.lb.apkparser

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lb.apkparser.library.ApkInfo
import java.util.*
import java.util.zip.ZipFile
import kotlin.concurrent.thread

private const val VALIDATE_RESOURCES = true

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null)
            performTest()
    }

    private fun performTest() {
        thread {
            val locale = Locale.getDefault()
            Log.d("AppLog", "getting all package infos:")
            var startTime = System.currentTimeMillis()
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            var endTime = System.currentTimeMillis()
            Log.d("AppLog", "time taken: ${endTime - startTime}")
            startTime = endTime
            var apksHandledSoFar = 0
            for (packageInfo in installedPackages) {
//                if (packageInfo.packageName != "com.facebook.katana")
//                    continue
//                if (packageInfo.packageName != "com.google.android.googlequicksearchbox")
//                    continue
                val metaData = packageInfo.applicationInfo.metaData
                val hasSplitApks = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !packageInfo.applicationInfo.splitPublicSourceDirs.isNullOrEmpty()
                val packageName = packageInfo.packageName
                Log.d("AppLog", "checking files of $packageName")
                packageInfo.applicationInfo.publicSourceDir.let { apkFilePath ->
//                    ApkFile(File(apkFilePath)).let {
//                        val manifestXml = it.manifestXml
//                        Log.d("AppLog", "")
//                    }
//                    ZipInputStreamFilter(ZipInputStream(FileInputStream(apkFilePath))).use {
                    com.lb.apkparser.library.ZipFileFilter(ZipFile(apkFilePath)).use {
                        val apkInfo = ApkInfo.getApkInfo(locale, it, true, VALIDATE_RESOURCES)
                        when {
                            apkInfo == null -> Log.e("AppLog", "can't parse apk:$apkFilePath")
                            apkInfo.apkType == null -> Log.e("AppLog", "can\'t get apk type: $apkFilePath ")
                            apkInfo.apkType == ApkInfo.ApkType.STANDALONE && hasSplitApks -> Log.e("AppLog", "detected as standalone, but in fact is base of split apks: $apkFilePath")
                            apkInfo.apkType == ApkInfo.ApkType.BASE_OF_SPLIT && !hasSplitApks -> Log.e("AppLog", "detected as base of split apks, but in fact is standalone: $apkFilePath")
                            apkInfo.apkType == ApkInfo.ApkType.SPLIT -> Log.e("AppLog", "detected as split apk, but in fact a main apk: $apkFilePath")
                            else -> {
                                val apkMeta = apkInfo.apkMetaTranslator.apkMeta
                                val labelOfLibrary = apkMeta.label ?: apkMeta.packageName
                                val apkMetaTranslator = apkInfo.apkMetaTranslator
                                @Suppress("ConstantConditionIf")
                                if (VALIDATE_RESOURCES) {
                                    val label = packageInfo.applicationInfo.loadLabel(packageManager)
                                    when {
                                        packageInfo.packageName != apkMeta.packageName -> Log.e("AppLog", "apk package name is different for $apkFilePath : correct one is: ${packageInfo.packageName} vs found: ${apkMeta.packageName}")
                                        packageInfo.versionName != apkMeta.versionName -> Log.e("AppLog", "apk version name is different for $apkFilePath : correct one is: ${packageInfo.versionName} vs found: ${apkMeta.versionName}")
                                        versionCodeCompat(packageInfo) != apkMeta.versionCode -> Log.e("AppLog", "apk version code is different for $apkFilePath : correct one is: ${versionCodeCompat(packageInfo)} vs found: ${apkMeta.versionCode}")
                                        label != labelOfLibrary -> Log.e("AppLog", "apk label is different for $apkFilePath : correct one is: $label vs found: $labelOfLibrary")
                                        else -> {
                                            Log.d("AppLog", "apk data of $apkFilePath : ${apkMeta.packageName}, ${apkMeta.versionCode}, ${apkMeta.versionName}, $labelOfLibrary, ${apkMeta.icon}, ${apkMetaTranslator.iconPaths}")
                                        }
                                    }
                                } else
                                    Log.d("AppLog", "apk data of $apkFilePath : ${apkMeta.packageName}, ${apkMeta.versionCode}, ${apkMeta.versionName}, $labelOfLibrary, ${apkMeta.icon}, ${apkMetaTranslator.iconPaths}")
                            }
                        }
                    }
                    ++apksHandledSoFar
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    packageInfo.applicationInfo.splitPublicSourceDirs?.forEach { apkFilePath ->
                        com.lb.apkparser.library.ZipFileFilter(ZipFile(apkFilePath)).use {
//                        ZipInputStreamFilter(ZipInputStream(FileInputStream(apkFilePath))).use {
                            val apkInfo = ApkInfo.getApkInfo(locale, it, requestParseManifestXmlTagForApkType = true, requestParseResources = false)
                            when {
                                apkInfo == null -> Log.e("AppLog", "can\'t parse apk:$apkFilePath")
                                apkInfo.apkType == null -> Log.e("AppLog", "can\'t get apk type: $apkFilePath")
                                apkInfo.apkType == ApkInfo.ApkType.STANDALONE -> Log.e("AppLog", "detected as standalone, but in fact is split apk: $apkFilePath")
                                apkInfo.apkType == ApkInfo.ApkType.BASE_OF_SPLIT -> Log.e("AppLog", "detected as base of split apks, but in fact is split apk: $apkFilePath")
                                apkInfo.apkType == ApkInfo.ApkType.BASE_OF_SPLIT_OR_STANDALONE -> Log.e("AppLog", "detected as base/standalone apk, but in fact is split apk: $apkFilePath")
                                else -> {
                                    val apkMeta = apkInfo.apkMetaTranslator.apkMeta
                                    val apkMetaTranslator = apkInfo.apkMetaTranslator
                                    when {
                                        packageInfo.packageName != apkMeta.packageName -> Log.e("AppLog", "apk package name is different for $apkFilePath : correct one is: ${packageInfo.packageName} vs found: ${apkMeta.packageName}")
                                        versionCodeCompat(packageInfo) != apkMeta.versionCode -> Log.e("AppLog", "apk version code is different for $apkFilePath : correct one is: ${versionCodeCompat(packageInfo)} vs found: ${apkMeta.versionCode}")
                                        else -> Log.d("AppLog", "apk data of $apkFilePath : ${apkMeta.packageName}, ${apkMeta.versionCode}, ${apkMeta.versionName}, ${apkMeta.name}, ${apkMeta.icon}, ${apkMetaTranslator.iconPaths}")
                                    }

                                }
                            }
                        }
                        ++apksHandledSoFar
                    }
                }
            }
            endTime = System.currentTimeMillis()
            Log.d("AppLog", "time taken: ${endTime - startTime} . handled ${installedPackages.size} apps apks:$apksHandledSoFar")
            Log.d("AppLog", "averageTime:${(endTime - startTime).toFloat() / installedPackages.size.toFloat()} per app ${(endTime - startTime).toFloat() / apksHandledSoFar.toFloat()} per APK")
            Log.e("AppLog", "done")
        }
    }

    companion object {
        fun versionCodeCompat(packageInfo: PackageInfo) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
    }
}