package com.music.bitchord.desktop

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopMacFrameTest {

    @Test
    fun testDarkAquaAppearance() {
        if (!DesktopPlatform.isMac) return
        val objc = NativeLibrary.getInstance("objc")
        val getClass = objc.getFunction("objc_getClass")
        val registerName = objc.getFunction("sel_registerName")
        val msgSend = objc.getFunction("objc_msgSend")

        val nsAppearanceClass = getClass.invokePointer(arrayOf("NSAppearance"))
        val nsStringClass = getClass.invokePointer(arrayOf("NSString"))
        val selStringWithUtf8 = registerName.invokePointer(arrayOf("stringWithUTF8String:"))
        val selAppearanceNamed = registerName.invokePointer(arrayOf("appearanceNamed:"))

        val darkAquaStr = msgSend.invokePointer(arrayOf(nsStringClass, selStringWithUtf8, "NSAppearanceNameDarkAqua"))
        val darkAppearance = msgSend.invokePointer(arrayOf(nsAppearanceClass, selAppearanceNamed, darkAquaStr))

        println("Dark Aqua Appearance: $darkAppearance")
        assertTrue(darkAppearance != null && darkAppearance != Pointer.NULL, "NSAppearanceNameDarkAqua must exist")
    }

    @Test
    fun testVisualEffectViewWithDarkAqua() {
        if (!DesktopPlatform.isMac) return
        assertTrue(DesktopMacFrame.isObjcAvailable(), "ObjC runtime classes must be available on macOS")

        val objc = NativeLibrary.getInstance("objc")
        val getClass = objc.getFunction("objc_getClass")
        val registerName = objc.getFunction("sel_registerName")
        val msgSend = objc.getFunction("objc_msgSend")

        val effectClass = getClass.invokePointer(arrayOf("NSVisualEffectView"))
        val selAlloc = registerName.invokePointer(arrayOf("alloc"))
        val selInit = registerName.invokePointer(arrayOf("init"))
        val alloc = msgSend.invokePointer(arrayOf(effectClass, selAlloc))
        val view = msgSend.invokePointer(arrayOf(alloc, selInit))
        assertTrue(view != null && view != Pointer.NULL, "NSVisualEffectView could not be created")

        val nsAppearanceClass = getClass.invokePointer(arrayOf("NSAppearance"))
        val nsStringClass = getClass.invokePointer(arrayOf("NSString"))
        val selStringWithUtf8 = registerName.invokePointer(arrayOf("stringWithUTF8String:"))
        val selAppearanceNamed = registerName.invokePointer(arrayOf("appearanceNamed:"))
        val darkAquaStr = msgSend.invokePointer(arrayOf(nsStringClass, selStringWithUtf8, "NSAppearanceNameDarkAqua"))
        val darkAppearance = msgSend.invokePointer(arrayOf(nsAppearanceClass, selAppearanceNamed, darkAquaStr))

        val selSetAppearance = registerName.invokePointer(arrayOf("setAppearance:"))
        msgSend.invoke(arrayOf(view, selSetAppearance, darkAppearance))

        val selSetMaterial = registerName.invokePointer(arrayOf("setMaterial:"))
        msgSend.invoke(arrayOf(view, selSetMaterial, 7L)) // Sidebar material

        val selSetWantsLayer = registerName.invokePointer(arrayOf("setWantsLayer:"))
        msgSend.invoke(arrayOf(view, selSetWantsLayer, true))

        val selLayer = registerName.invokePointer(arrayOf("layer"))
        val layer = msgSend.invokePointer(arrayOf(view, selLayer))
        assertTrue(layer != null && layer != Pointer.NULL, "Layer must be present on NSVisualEffectView")

        val selSetCornerRadius = registerName.invokePointer(arrayOf("setCornerRadius:"))
        val selSetMasksToBounds = registerName.invokePointer(arrayOf("setMasksToBounds:"))
        msgSend.invoke(arrayOf(layer, selSetCornerRadius, 10.0))
        msgSend.invoke(arrayOf(layer, selSetMasksToBounds, true))
        println("Successfully tested NSVisualEffectView with Dark Aqua and corner radius 10.0")
    }

    @Test
    fun testPerformWindowDragSelectors() {
        if (!DesktopPlatform.isMac) return
        val objc = NativeLibrary.getInstance("objc")
        val registerName = objc.getFunction("sel_registerName")
        val selPerformDrag = registerName.invokePointer(arrayOf("performWindowDragWithEvent:"))
        val selCurrentEvent = registerName.invokePointer(arrayOf("currentEvent"))
        assertTrue(selPerformDrag != null && selPerformDrag != Pointer.NULL, "performWindowDragWithEvent: must be a valid selector")
        assertTrue(selCurrentEvent != null && selCurrentEvent != Pointer.NULL, "currentEvent must be a valid selector")
    }

    @Test
    fun testSetBackdropMaterials() {
        if (!DesktopPlatform.isMac) return
        assertTrue(DesktopMacFrame.setBackdrop(2), "Setting Mica material must succeed")
        assertTrue(DesktopMacFrame.setBackdrop(3), "Setting Acrylic material must succeed")
        assertTrue(DesktopMacFrame.setBackdrop(0), "Setting Off material must succeed")
        assertTrue(DesktopMacFrame.setBackdrop(2), "Setting back to Mica must succeed seamlessly")
    }

    @Test
    fun testActivateAppSelectors() {
        if (!DesktopPlatform.isMac) return
        val objc = NativeLibrary.getInstance("objc")
        val registerName = objc.getFunction("sel_registerName")
        val getClass = objc.getFunction("objc_getClass")
        val msgSend = objc.getFunction("objc_msgSend")

        val selActivate = registerName.invokePointer(arrayOf("activateIgnoringOtherApps:"))
        val selIsMiniaturized = registerName.invokePointer(arrayOf("isMiniaturized"))
        val selDeminiaturize = registerName.invokePointer(arrayOf("deminiaturize:"))
        val selMakeKeyAndOrderFront = registerName.invokePointer(arrayOf("makeKeyAndOrderFront:"))
        val selInvalidateShadow = registerName.invokePointer(arrayOf("invalidateShadow"))

        assertTrue(selActivate != null && selActivate != Pointer.NULL, "activateIgnoringOtherApps: must be a valid selector")
        assertTrue(selIsMiniaturized != null && selIsMiniaturized != Pointer.NULL, "isMiniaturized must be a valid selector")
        assertTrue(selDeminiaturize != null && selDeminiaturize != Pointer.NULL, "deminiaturize: must be a valid selector")
        assertTrue(selMakeKeyAndOrderFront != null && selMakeKeyAndOrderFront != Pointer.NULL, "makeKeyAndOrderFront: must be a valid selector")
        assertTrue(selInvalidateShadow != null && selInvalidateShadow != Pointer.NULL, "invalidateShadow must be a valid selector")

        val nsAppClass = getClass.invokePointer(arrayOf("NSApplication"))
        val selSharedApp = registerName.invokePointer(arrayOf("sharedApplication"))
        val app = msgSend.invokePointer(arrayOf(nsAppClass, selSharedApp))
        assertTrue(app != null && app != Pointer.NULL, "NSApplication sharedApplication must return valid pointer")
    }

    @Test
    fun testDesktopWindowVisibilityLifecycle() {
        DesktopWindowVisibility.install()
        DesktopWindowVisibility.keepRunningWhenClosed = true

        val closeResult = DesktopWindowVisibility.onCloseRequest()
        assertTrue(!closeResult, "onCloseRequest must return false when keepRunningWhenClosed is true")
        assertTrue(!DesktopWindowVisibility.visible.value, "Window must be hidden when closed to tray")

        DesktopWindowVisibility.show()
        assertTrue(DesktopWindowVisibility.visible.value, "show() must make the window visible again")
    }
}
