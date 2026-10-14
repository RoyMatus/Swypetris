# Launcher icon and RuStore

Use **store/rustore-icon-512.png** for the listing. The file is an opaque RGB PNG, 512×512 (1:1), 557080 bytes, with full-bleed background and no pre-rounded corners. Its exact bytes are also supplied as android/res/drawable-nodpi/ic_launcher_artwork.png.

This is a new app icon based on the approved cathedral artwork. It does not replace swypetris_logo.png or add a logo to any in-game menu. Existing wordmark cropping and intro remain unchanged.

## Ready launcher resources

The package includes legacy ic_launcher.xml / ic_launcher_round.xml, adaptive versions in drawable-v26, menu_launcher_foreground.xml and menu_launcher_colors.xml. They retain the manifest's current resource names. The existing ic_launcher_monochrome.xml is copied unchanged as a compatibility resource.

The adaptive foreground insets the same square artwork by 16.6667% on each side, mapping it into the inner 72×72 of a 108×108 layer. Its cathedral occupies less than the 66×66 protected central region; the outer background fill is #001581. This avoids the cross being enlarged beyond the visible mask by the old full-layer artwork setup. Do not add a second scale/inset around this wrapper.

Verify circular, squircle and rounded-square masks, small icon sizes, the normal-color launcher and system-themed monochrome mode on Android. The existing monochrome cathedral is retained; Android controls its color. Normal-color launcher and store use the same artwork. Do not manually round the PNG or place text/badges over it.

## Official requirements checked 2026-10-14

RuStore's media requirement specifies 512×512, PNG/JPG, at most 3 MB and a filled background. It also requires the store and installed app to use the same icon. This concerns the installed application icon; it does not require adding an icon to the game's own menu.

Sources:
- [RuStore application requirements, section 6.6](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps)
- [Android adaptive icon design](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Android InsetDrawable](https://developer.android.com/reference/android/graphics/drawable/InsetDrawable)

The provided file passes the measurable format checks. No RuStore upload, moderation approval or installed-app acceptance is claimed. During the later release, submit this same file together with the APK containing the supplied artwork and verify the installed icon.
