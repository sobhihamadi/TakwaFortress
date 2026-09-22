package com.example.takwafortress.model.entities

import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.model.interfaces.IIdentifiable

/**
 * Enhanced BlockedApp entity supporting:
 * 1. Installed apps (can be blocked immediately)
 * 2. Pre-blocked apps (will be blocked when installed)
 */
open class BlockedApp(
    private val packageName: String,
    private val appName: String,
    private val isSystemApp: Boolean,
    private val isSuspended: Boolean,
    private val blockReason: String,
    private val detectedDate: Long,
    private val isInstalled: Boolean = true,  // ✅ NEW: Track if app is actually installed
    private val isPreBlocked: Boolean = false // ✅ NEW: Was this added before installation?
) {

    fun getPackageName(): String = packageName
    fun getAppName(): String = appName
    fun getIsSystemApp(): Boolean = isSystemApp
    fun getIsSuspended(): Boolean = isSuspended
    fun getBlockReason(): String = blockReason
    fun getDetectedDate(): Long = detectedDate
    fun getIsInstalled(): Boolean = isInstalled
    fun getIsPreBlocked(): Boolean = isPreBlocked

    fun canBeUninstalled(): Boolean = !isSystemApp

    fun isBlacklistedApp(): Boolean {
        return packageName in NUCLEAR_BLACKLIST
    }

    fun shouldBeHidden(): Boolean {
        // Hide if nuclear AND installed
        return isBlacklistedApp() && isInstalled
    }

    fun shouldBeSuspended(): Boolean {
        // Suspend if not blacklisted, installed, and marked as suspended
        return !isBlacklistedApp() && isSuspended && isInstalled
    }

    /**
     * ✅ NEW: Check if this is a "waiting to block" entry
     */
    fun isPendingBlock(): Boolean {
        return isPreBlocked && !isInstalled
    }

    companion object {
        val NUCLEAR_BLACKLIST = setOf(
            "org.telegram.messenger",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.discord",

            // ── Added from all_apps_and_sites.docx ──────────────────────
            "org.thunderdog.challegram",
            "proxy.browser.unblock.sites.proxybrowser.unblocksites",
            "org.plus18.android",
            "chat.revolt",
            "xyz.blueskyweb.app",
            "org.joinmastodon.android",
            "videoplayer.videodownloader.downloader",
            "com.neurobro.browser",
            "com.vivaldi.browser",
            "com.dajiu.stay",
            "net.quetta.browser",
            "com.ycngmn.weblo",
            "com.libertyvaults.wiko",
            "com.startpage.app",
            "company.thebrowser.arc",
            "com.brave.browser_nightly",
            "io.friendly",
            "com.deep.search.browser",
            "com.chimbori.hermitcrab",
            "com.google.ar.core",
            "io.friendly.twitter",
            "org.torproject.torbrowser",
            "net.onecook.browser",
            "com.medium.reader",
            "com.hootsuite.droid.full",
            "org.buffer.android",
            "com.fedica.android",
            "com.allsocialmediaapp.fastechpointapp",
            "browser.vego.me",
            "com.all.one.social.media.network",
            "com.a_studio.socialmediapro",
            "socialallinoneapp.allsocialmediaapps",
            "com.socialmedia.allsocial",
            "tutorials.seoschemes.sonyaath",
            "messenger.video.call.chat.free",
            "com.social.hemapp",
            "social.app.india2010",
            "com.technopath.myapplication",
            "socialpilot.co",
            "com.rainbow.aiobrowser",
            "my.socialmedia",
            "com.ronstech.allsocialmediaapp",
            "com.sclbrd.app",
            "com.all_social_media.social_media",
            "dev.liukkonen.socialwrap",
            "com.socialbu.app",
            "io.publer",
            "com.vistasocial.android",
            "com.sec.android.app.samsungapps",
            "the.best.gram",
            "com.sociallite.android",
            "org.vidogram.lite",
            "com.pl.premierleague",
            "com.facebook.katana",
            "jp.ejimax.berrybrowser",
            "com.ask.browser.ai",
            "com.panalinks.webkey",
            "com.nktnet.webview_kiosk",
            "com.tcl.browser",
            "de.ozerov.fully",
            "com.transsion.phoenix",
            "com.snc.test.webview2",
            "com.webview.space",
            "com.tph.webViewappbuilder",
            "com.brouken.websearch",
            "com.webtoapp.converter",
            "com.webviewstudio.app",
            "com.webtoapp.convertwebsitetoapp",
            "com.xckevin.android.app.webview.test",
            "com.atriidev.webtoappconverter",
            "com.nexech.webview.basic",
            "com.nexech.webview.premium",
            "com.appbin.webviewworld",
            "com.nexech.webview.standard",
            "com.AcmuStudio.webviewtest",
            "com.webtoapp.converter.appmaker",
            "com.webviewtest.app",
            "com.azc.terminet",
            "com.sec.android.app.wlantest",
            "mgks.os.swv",
            "com.chrome.dev",
            "com.brgwebview.sa",
            "com.satisfilabs.webclient",
            "com.studentpasscard.driverappw",
            "com.ncnp.app",
            "com.webviewnova.app",
            "com.samsung.android.inputshare",
            "ua.tiar.devsurf",
            "com.probuse.odoo",
            "com.sec.epdgtestapp",
            "ooo.alienz.devinspector",
            "com.freekiosk",
            "com.rikpro.free",
            "com.freewebtoapk",
            "com.talpa.hibrowser",
            "com.phlox.tvwebbrowser",
            "com.nexaford.webstore",
            "com.webviewcli",
            "com.jcd.webvisiona28",
            "com.app.dr1009.webviewchecker",
            "com.multipower.multiwebview",
            "com.letsgoup.uServeWebview",
            "com.exocad.webview",
            "com.google.android.apps.searchlite",
            "com.craftonative.webtoapp.converter.appbuilder.websitetoapp",
            "com.ssolstice.browser",
            "de.mxapplications.kmp.app.webdevstudio",
            "com.lozsolutiont.htmlviewer",
            "com.explore.web.browser",
            "reactivephone.msearch",
            "com.instantbits.cast.webvideo",
            "com.opera.gx",
            "com.fly.web.smart.browser",
            "com.qwant.liberty",
            "com.cloudmosa.puffinFree",
            "com.kagi.search",
            "com.goodtoolapps.zeus",
            "org.mozilla.fenix",
            "com.aospstudio.quicksearch",
            "com.ecosia.android",
            "com.kagi.smallweb",
            "com.devhomc.search",
            "rk.android.app.pixelsearch",
            "com.zerch.widget",
            "com.yep.search",
            "com.chrome.canary",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.duckduckgo.mobile.android",
            "com.fooview.android.fooview",
            "shareit.lite",
            "com.quora.android"
        )

        fun isNuclearApp(packageName: String): Boolean {
            return packageName in NUCLEAR_BLACKLIST
        }
    }
}

class IdentifierBlockedApp(
    private val id: ID,
    packageName: String,
    appName: String,
    isSystemApp: Boolean,
    isSuspended: Boolean,
    blockReason: String,
    detectedDate: Long,
    isInstalled: Boolean = true,
    isPreBlocked: Boolean = false
) : BlockedApp(
    packageName,
    appName,
    isSystemApp,
    isSuspended,
    blockReason,
    detectedDate,
    isInstalled,
    isPreBlocked
), IIdentifiable {

    override fun getId(): ID = id
}