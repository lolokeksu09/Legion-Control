package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri

data class AppPackageObservation(
    val packageName:String,
    val displayName:String,
    val versionName:String?,
    val versionCode:Long?,
    val installedAtMs:Long?,
    val updatedAtMs:Long?,
    val systemApp:Boolean?,
    val enabled:Boolean?,
    val batteryExempt:Boolean?,
    val requestedPermissions:Int?,
    val grantedPermissions:Int?,
    val permissionDetails:List<String>,
    val visible:Boolean
) {
    val fingerprint:AppFingerprint get() =
        AppFingerprint(visible,if(visible)versionCode else null,
            if(visible)updatedAtMs else null)

    fun report():String=buildString {
        append("APPLICATION CONTROL CENTER · ").append(displayName).append("\n")
        append("Пакет: ").append(packageName).append("\n")
        append("Видимость Android PackageManager: ")
            .append(if(visible)"есть" else "недоступно / неизвестно").append("\n")
        if(visible) {
            append("Версия: ").append(ApplicationAuditRules.versionLabel(versionName,versionCode)).append("\n")
            append("Установка (Android): ").append(installedAtMs ?: "н/д").append(" мс Unix\n")
            append("Обновление (Android): ").append(updatedAtMs ?: "н/д").append(" мс Unix\n")
            append("Системный пакет: ").append(systemApp?.toString() ?: "н/д").append("\n")
            append("Включён Android: ").append(enabled?.toString() ?: "н/д").append("\n")
            append("Исключение из оптимизации батареи: ").append(
                batteryExempt?.let { if(it)"да" else "нет" } ?: "неизвестно"
            ).append("\n")
            append("Разрешения в PackageInfo: ").append(requestedPermissions ?: "н/д")
                .append(" · флаг GRANTED: ").append(grantedPermissions ?: "н/д").append("\n")
            if(permissionDetails.isNotEmpty()) {
                append("Запрашиваемые разрешения (видимые Android):\n")
                permissionDetails.take(60).forEach { append("• ").append(it).append("\n") }
            }
        } else append("Android может скрывать пакет из-за ограничений видимости. "+
            "Это НЕ доказательство удаления, отключения или блокировки.\n")
        append("Статус разрешений другого приложения по API может быть неполным. "+
            "Legion Control не меняет и не отзывает их автоматически.")
    }
}

data class LaunchableApp(val packageName:String,val label:String,val isGame:Boolean=false)

/** PackageManager public API and Android Settings intents only. */
object ApplicationInventory {
    fun isPackageName(pkg:String):Boolean=SmartProfilesRepository.validatePackage(pkg)

    fun launchable(context:Context):List<LaunchableApp> {
        val pm=context.packageManager
        val query=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(query,PackageManager.ResolveInfoFlags.of(0L))
                .asSequence()
                .filter { it.activityInfo?.packageName?.let { pkg -> pkg!=context.packageName }==true }
                .map { info -> LaunchableApp(
                    info.activityInfo.packageName,
                    info.loadLabel(pm).toString().take(100),
                    info.activityInfo.applicationInfo?.category==ApplicationInfo.CATEGORY_GAME
                ) }
                .distinctBy { it.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .take(600).toList()
        }.getOrDefault(emptyList())
    }

    fun inspect(context:Context,pkg:String):AppPackageObservation {
        require(isPackageName(pkg))
        val pm=context.packageManager
        val packageInfo=runCatching {
            pm.getPackageInfo(pkg,PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PERMISSIONS.toLong()))
        }.getOrNull()
        if(packageInfo==null) return AppPackageObservation(
            pkg,pkg,null,null,null,null,null,null,null,null,null,emptyList(),false)
        val app=packageInfo.applicationInfo
        val label=runCatching { app?.loadLabel(pm)?.toString()?.take(100) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: pkg
        val permissions=packageInfo.requestedPermissions
        val flags=packageInfo.requestedPermissionsFlags
        val descriptions=permissions?.mapIndexed { index,p ->
            val granted=index < (flags?.size ?: 0) &&
                flags!![index] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED!=0
            p.take(170)+": "+if(granted)"GRANTED (флаг)" else "не подтверждено флагом"
        }.orEmpty()
        val battery=runCatching {
            val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.isIgnoringBatteryOptimizations(pkg)
        }.getOrNull()
        return AppPackageObservation(
            pkg,label,packageInfo.versionName,packageInfo.longVersionCode,
            packageInfo.firstInstallTime.takeIf { it>0 },
            packageInfo.lastUpdateTime.takeIf { it>0 },
            app?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 },
            app?.enabled,battery,permissions?.size,
            permissions?.indices?.count { i ->
                i < (flags?.size ?: 0) &&
                    flags!![i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED!=0
            },descriptions.take(60),true
        )
    }

    fun appInfo(context:Context,pkg:String):Intent {
        require(isPackageName(pkg))
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$pkg"))
    }

    fun notifications(context:Context,pkg:String):Intent {
        require(isPackageName(pkg))
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE,pkg)
        }
    }

    fun batterySettings():Intent=Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun launchIntent(context:Context,pkg:String):Intent? {
        require(isPackageName(pkg))
        return context.packageManager.getLaunchIntentForPackage(pkg)
    }
}
