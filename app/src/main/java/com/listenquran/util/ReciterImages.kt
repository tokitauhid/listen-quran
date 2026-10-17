package com.listenquran.util

object ReciterImages {

    fun getPhotoUrl(reciterId: Int, englishName: String, existingPhotoUrl: String? = null): String? {
        if (!existingPhotoUrl.isNullOrBlank() && !existingPhotoUrl.matches(Regex(""".*/images/reciters/\d+\.jpg"""))) {
            return existingPhotoUrl
        }

        val lower = englishName.lowercase()
        return when {
            reciterId == 7 || lower.contains("afasy") || lower.contains("afasi") ->
                "https://static.qurancdn.com/images/reciters/6/mishary-rashid-alafasy-profile.jpeg"

            reciterId in listOf(1, 2) || lower.contains("abdulbaset") || lower.contains("abdulsamad") || lower.contains("abdelbasset") ->
                "https://static.qurancdn.com/images/reciters/1/abdelbasset-profile.jpeg"

            reciterId == 3 || lower.contains("sudais") ->
                "https://static.qurancdn.com/images/reciters/2/abdul-rahman-al-sudais-profile.jpeg"

            reciterId == 4 || lower.contains("shatri") || lower.contains("shaatree") ->
                "https://static.qurancdn.com/images/reciters/3/abu-bakr-al-shatri-pofile.jpeg"

            reciterId == 5 || lower.contains("rifai") || lower.contains("rifaa") ->
                "https://static.qurancdn.com/images/reciters/4/hani-ar-rifai-profile.jpeg"

            reciterId in listOf(6, 12, 1118) || lower.contains("husary") || lower.contains("hussary") ->
                "https://static.qurancdn.com/images/reciters/5/mahmoud-khalil-al-hussary-profile.png"

            reciterId in listOf(8, 9, 168, 1112) || lower.contains("minshawi") ->
                "https://static.qurancdn.com/images/reciters/7/mohamed-siddiq-el-minshawi-profile.jpeg"

            reciterId in listOf(10, 1031) || lower.contains("shuraym") || lower.contains("shuraim") ->
                "https://static.qurancdn.com/images/reciters/8/saoud-shuraim-profile.jpeg"

            reciterId in listOf(161, 1024) || lower.contains("tunaiji") ->
                "https://static.qurancdn.com/images/reciters/11/khalifa-al-tunaiji-profile.jpeg"

            reciterId in listOf(13, 1030) || lower.contains("ghamdi") || lower.contains("ghamidi") ->
                "https://static.qurancdn.com/images/reciters/16/saad-al-ghamdi-profile.png"

            reciterId in listOf(174, 1092, 1146) || lower.contains("dosari") || lower.contains("dussary") ->
                "https://static.qurancdn.com/images/reciters/20/yasser-profile.png"

            reciterId in listOf(19, 1005, 1019) || lower.contains("ajmy") || lower.contains("ajami") ->
                "https://static.qurancdn.com/images/reciters/22/Ahmed-ibn-Ali-al-Ajmy-profile.png"

            reciterId in listOf(158, 1023, 1076) || lower.contains("jabir") || lower.contains("jaber") ->
                "https://static.qurancdn.com/images/reciters/23/Abdullah-Ali-Jabir-profile.png"

            reciterId in listOf(160, 1217) || lower.contains("baleela") || lower.contains("balila") || lower.contains("balilah") ->
                "https://static.qurancdn.com/images/reciters/24/Bandar-Baleela-profile.png"

            reciterId in listOf(159, 1102) || lower.contains("muaiqly") || lower.contains("meaqli") || lower.contains("muayqili") ->
                "https://static.qurancdn.com/images/reciters/25/Maher-al-Muaiqly-profile.png"

            reciterId == 175 || lower.contains("sharida") ->
                "https://static.qurancdn.com/images/reciters/26/Abdullah-Hamad-Abu-Sharida-profile.png"

            reciterId == 176 || lower.contains("tahoun") ->
                "https://static.qurancdn.com/images/reciters/27/ahmed-abdelhamid-tahoun-profile.jpg"

            else -> null
        }
    }
}
