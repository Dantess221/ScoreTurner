android {
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("ANDROID_SIGNING_STORE_FILE")
            val storePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
            val keyAlias      = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
            val keyPassword   = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")
            if (!storeFilePath.isNullOrBlank()
                && !storePassword.isNullOrBlank()
                && !keyAlias.isNullOrBlank()
                && !keyPassword.isNullOrBlank()
                && file(storeFilePath).exists()
            ) {
                storeFile = file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // метка что подписи нет
                enableV1Signing = false
                enableV2Signing = false
                enableV3Signing = false
                enableV4Signing = false
            }
        }
    }
    buildTypes {
        getByName("release") {
            // подключаем подпись только если storeFile задан
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}
