// Fichier de build racine — d\u00e9clare les plugins partag\u00e9s sans les appliquer ici.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ksp) apply false // Phase 4 : KSP pour Room
}
