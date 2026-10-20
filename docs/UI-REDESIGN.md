# Settings, Projects and appearance screens

Settings now uses rounded preference/support cards, icon tiles, notification controls and the existing connected-service privacy actions. Projects has compact filter/sort/layout controls, a native folder illustration and responsive two/three/four-column cards. Search and favorites remain removed.

Language and Theme use selectable cards and explicit Apply buttons. Theme cards preview Gold, Light, Dark and Violet with original Canvas swatches. Back leaves saved preferences untouched; draft selection is retained across activity restoration. New labels are localized in Urdu, Hindi and Chinese.

Validation: ads-enabled compileDebugKotlin and compileDebugAndroidTestKotlin passed offline. Resource XML and translation JSON validated. Existing appearance smoke assertions were adapted to cards and deferred preference saving. Instrumentation tests were compiled, not executed. Device appearance checks are pending. The 1.17 testing APK includes this redesign; the earlier 1.16 APK predates it.
