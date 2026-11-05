package ir.foodcost.data

/**
 * Made by scripts/legacy/Generate074.kt with the code of 0.7.4 itself (commit ca6ae78), not by
 * hand: the operations of a kitchen as 0.7.4 stored them, one JSON text each, in the order it
 * stored them, and the menu figures 0.7.4 worked out from them for each day. Do not edit.
 */
object Legacy074 {
    /** The period 0.7.4 closed, through this day. */
    const val CLOSED_THROUGH = "2026-10-31"

    val operations: List<String> = listOf(
        """{"id":"51094a5a-025a-440d-a83d-d0e2345c095c","at":{"wall":1790000060000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"IngredientSaved","data":{"id":"117e7ec6-49b2-4d44-aaa5-c509d989385f","name":"گوشت گوسفندی","baseDimension":"MASS","category":null,"yields":[{"name":"پاک کردن","ratio":0.9}],"conversions":{},"archived":false},"seq":1}""",
        """{"id":"f04bae61-d4d3-4e96-8a00-0d0cef6c6d84","at":{"wall":1790000120000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"IngredientSaved","data":{"id":"cfc57197-33ef-4f91-8eb7-4cafebf12201","name":"برنج طارم","baseDimension":"MASS","category":null,"yields":[{"name":"شستن","ratio":0.98}],"conversions":{},"archived":false},"seq":2}""",
        """{"id":"f2484ca6-8f19-42ad-b00d-95c174f9b9b9","at":{"wall":1790000180000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"IngredientSaved","data":{"id":"21b2e7f6-fed4-4204-9726-692058ba5735","name":"پیاز","baseDimension":"MASS","category":null,"yields":[],"conversions":{},"archived":false},"seq":3}""",
        """{"id":"3493a4d4-6af0-48e7-8b19-f1971e52b295","at":{"wall":1790000240000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"PriceRecorded","data":{"priceId":"c3f04ed7-29ef-492b-b65f-d531f00be47e","price":{"ingredientId":"117e7ec6-49b2-4d44-aaa5-c509d989385f","effectiveFrom":"2026-10-01","packageQty":1,"packageUom":"KILOGRAM","grossRials":16000000,"discountRials":0,"taxRials":0,"taxRecoverable":false,"deliveryRials":0}},"seq":4}""",
        """{"id":"8c118da7-6f5d-4c55-a12c-9b93bb864d22","at":{"wall":1790000300000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"PriceRecorded","data":{"priceId":"95307d4c-8170-4e04-ab65-13721f5e4d40","price":{"ingredientId":"cfc57197-33ef-4f91-8eb7-4cafebf12201","effectiveFrom":"2026-10-01","packageQty":10,"packageUom":"KILOGRAM","grossRials":46000000,"discountRials":0,"taxRials":0,"taxRecoverable":false,"deliveryRials":0}},"seq":5}""",
        """{"id":"2113252c-0731-4ae7-893f-1d421a0201e1","at":{"wall":1790000360000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"PriceRecorded","data":{"priceId":"4ecd7f6e-f5b9-4410-87fe-607db8a85888","price":{"ingredientId":"21b2e7f6-fed4-4204-9726-692058ba5735","effectiveFrom":"2026-10-01","packageQty":1,"packageUom":"KILOGRAM","grossRials":800000,"discountRials":0,"taxRials":0,"taxRecoverable":false,"deliveryRials":0}},"seq":6}""",
        """{"id":"e3e5f4af-afca-4459-b5ef-729ad171ddbb","at":{"wall":1790000420000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"PriceRecorded","data":{"priceId":"1c80449d-6199-403c-a061-6b55ad3adfef","price":{"ingredientId":"117e7ec6-49b2-4d44-aaa5-c509d989385f","effectiveFrom":"2026-10-15","packageQty":1,"packageUom":"KILOGRAM","grossRials":18000000,"discountRials":0,"taxRials":0,"taxRecoverable":false,"deliveryRials":0}},"seq":7}""",
        """{"id":"941d45f7-d283-49fc-9b5b-42119fe2962e","at":{"wall":1790000480000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeSaved","data":{"id":"ca6a0b00-a2df-4c81-9904-e3c1ea53b5de","name":"چلو","kind":"PREP","category":null,"pricing":null,"archived":false},"seq":8}""",
        """{"id":"c4c52c4a-49a5-4374-8b21-965283a54b19","at":{"wall":1790000540000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeSaved","data":{"id":"806463ff-a724-49e1-aae3-182f04ea49ac","name":"چلوکباب","kind":"MENU_ITEM","category":"main","pricing":null,"archived":false},"seq":9}""",
        """{"id":"677ddd50-4efd-4132-9a9b-a9efb45712db","at":{"wall":1790000600000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeSaved","data":{"id":"fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6","name":"خورش","kind":"MENU_ITEM","category":"main","pricing":null,"archived":false},"seq":10}""",
        """{"id":"2d6dfa1c-3485-4584-9a74-dfcd979d66c1","at":{"wall":1790000660000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeSaved","data":{"id":"a3486ca5-dfd9-407c-8260-c3bf8628b028","name":"سالاد شیرازی","kind":"MENU_ITEM","category":null,"pricing":null,"archived":false},"seq":11}""",
        """{"id":"c1f9bb03-1d1a-4f56-b5c4-10d3f5e289ef","at":{"wall":1790000720000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeVersionSaved","data":{"recipeId":"ca6a0b00-a2df-4c81-9904-e3c1ea53b5de","version":{"validFrom":"2026-10-01","portions":1,"batchLines":[{"ingredient":"cfc57197-33ef-4f91-8eb7-4cafebf12201","qty":150,"uom":"GRAM","basis":"USABLE"}],"portionLines":[],"miscPercent":0,"note":"","extras":[]}},"seq":12}""",
        """{"id":"3d7a4fdc-7d0c-47c1-9649-bc2583744e89","at":{"wall":1790000780000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeVersionSaved","data":{"recipeId":"806463ff-a724-49e1-aae3-182f04ea49ac","version":{"validFrom":"2026-10-01","portions":1,"batchLines":[{"ingredient":"117e7ec6-49b2-4d44-aaa5-c509d989385f","qty":220,"uom":"GRAM","basis":"USABLE"},{"ingredient":"21b2e7f6-fed4-4204-9726-692058ba5735","qty":30,"uom":"GRAM","basis":"USABLE"},{"recipe":"ca6a0b00-a2df-4c81-9904-e3c1ea53b5de","qty":1,"uom":"PORTION","basis":"USABLE"}],"portionLines":[],"note":"","extras":[]}},"seq":13}""",
        """{"id":"31975e0f-c14a-41dd-be8a-df23980ca919","at":{"wall":1790000840000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeVersionSaved","data":{"recipeId":"fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6","version":{"validFrom":"2026-10-01","portions":1,"batchLines":[{"ingredient":"117e7ec6-49b2-4d44-aaa5-c509d989385f","qty":120,"uom":"GRAM","basis":"USABLE"},{"ingredient":"21b2e7f6-fed4-4204-9726-692058ba5735","qty":60,"uom":"GRAM","basis":"USABLE"},{"recipe":"ca6a0b00-a2df-4c81-9904-e3c1ea53b5de","qty":1,"uom":"PORTION","basis":"USABLE"}],"portionLines":[],"note":"","extras":[]}},"seq":14}""",
        """{"id":"62c77afc-1285-41b6-8c22-8ba1991ea52d","at":{"wall":1790000900000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeVersionSaved","data":{"recipeId":"a3486ca5-dfd9-407c-8260-c3bf8628b028","version":{"validFrom":"2026-10-01","portions":1,"batchLines":[{"ingredient":"21b2e7f6-fed4-4204-9726-692058ba5735","qty":100,"uom":"GRAM","basis":"USABLE"}],"portionLines":[],"miscPercent":0,"note":"","extras":[]}},"seq":15}""",
        """{"id":"a3cdf380-4939-4836-beb0-cc59c7c45ff1","at":{"wall":1790000960000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"MenuPriceSet","data":{"recipeId":"806463ff-a724-49e1-aae3-182f04ea49ac","effectiveFrom":"2026-10-01","rials":13500000},"seq":16}""",
        """{"id":"7cc4ceeb-6c49-4f30-9463-ea6505b26455","at":{"wall":1790001020000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"MenuPriceSet","data":{"recipeId":"fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6","effectiveFrom":"2026-10-01","rials":9000000},"seq":17}""",
        """{"id":"6604bf4f-7c44-4780-a0ed-063647ea1a54","at":{"wall":1790001080000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"MenuPriceSet","data":{"recipeId":"a3486ca5-dfd9-407c-8260-c3bf8628b028","effectiveFrom":"2026-10-01","rials":2500000},"seq":18}""",
        """{"id":"f4947da5-98a9-44be-a02a-b746fab7b83b","at":{"wall":1790001140000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"SettingsSaved","data":{"costing":{"defaultMiscPercent":4,"miscPercentByCategory":{}},"effectiveFrom":"2026-10-15"},"seq":19}""",
        """{"id":"da2de015-823b-4492-a1b1-ae6acdb58be2","at":{"wall":1790001200000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"IngredientSaved","data":{"id":"117e7ec6-49b2-4d44-aaa5-c509d989385f","yields":[{"name":"پاک کردن","ratio":0.85}],"effectiveFrom":"2026-11-02"},"seq":20}""",
        """{"id":"2d635e66-5b2a-442a-ae22-52ed48e1f81c","at":{"wall":1790001260000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"PriceRecorded","data":{"priceId":"5f1944c3-8db9-4e55-93c5-22a71e14b5a9","price":{"ingredientId":"21b2e7f6-fed4-4204-9726-692058ba5735","effectiveFrom":"2026-11-02","packageQty":1,"packageUom":"KILOGRAM","grossRials":900000,"discountRials":0,"taxRials":0,"taxRecoverable":false,"deliveryRials":0}},"seq":21}""",
        """{"id":"c870df8a-666a-42cc-ae96-5777fb3210a7","at":{"wall":1790001320000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"MenuPriceSet","data":{"recipeId":"806463ff-a724-49e1-aae3-182f04ea49ac","effectiveFrom":"2026-11-02","rials":14500000},"seq":22}""",
        """{"id":"cbc1f29d-79d5-4c6e-ba6d-6ce1ff9528d2","at":{"wall":1790001380000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeSaved","data":{"id":"fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6","name":"خورش قیمه","kind":"MENU_ITEM","category":"main","effectiveFrom":"2026-11-02"},"seq":23}""",
        """{"id":"e18ab3cf-354f-41de-8b12-2a64ed85406c","at":{"wall":1790001440000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"RecipeSaved","data":{"id":"a3486ca5-dfd9-407c-8260-c3bf8628b028","name":"سالاد شیرازی","kind":"PREP","category":null,"effectiveFrom":"2026-11-02"},"seq":24}""",
        """{"id":"7d0ddb87-a426-400d-8bdf-3d6bbdb98765","at":{"wall":1790001500000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"PeriodClosed","data":{"through":"2026-10-31","snapshot":{"date":"2026-10-31","items":[{"recipeId":"806463ff-a724-49e1-aae3-182f04ea49ac","name":"چلوکباب","foodCostRials":5305042,"menuPriceRials":13500000,"suggestedPriceRials":16700000,"foodCostPercent":43.23,"status":"UNDER"},{"recipeId":"fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6","name":"خورش قیمه","foodCostRials":3250002,"menuPriceRials":9000000,"suggestedPriceRials":10300000,"foodCostPercent":39.72,"status":"UNDER"}]},"seen":{"phone":{"wall":1790001440000,"counter":0,"seq":24,"unnumbered":[]}}},"seq":25}""",
        """{"id":"01e49a21-e6e9-4deb-8af3-868ce4832133","at":{"wall":1790001560000,"counter":0,"device":"phone"},"actor":{"id":"owner","role":"OWNER"},"type":"PriceRecorded","data":{"priceId":"5a832acc-6dc5-4ceb-bd07-8c1d98907756","price":{"ingredientId":"117e7ec6-49b2-4d44-aaa5-c509d989385f","effectiveFrom":"2026-11-10","packageQty":1,"packageUom":"KILOGRAM","grossRials":20000000,"discountRials":0,"taxRials":0,"taxRecoverable":false,"deliveryRials":0}},"seq":26}""",
    )

    /** For each day, each dish of the menu: id|name|cost|menu price|suggested price|food cost %|status. */
    val figures: Map<String, List<String>> = mapOf(
        "2026-10-01" to listOf(
            "806463ff-a724-49e1-aae3-182f04ea49ac|چلوکباب|4757246|13500000|15000000|38.76|UNDER",
            "fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6|خورش قیمه|2950855|9000000|9300000|36.07|NEAR",
        ),
        "2026-10-15" to listOf(
            "806463ff-a724-49e1-aae3-182f04ea49ac|چلوکباب|5305042|13500000|16700000|43.23|UNDER",
            "fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6|خورش قیمه|3250002|9000000|10300000|39.72|UNDER",
        ),
        "2026-10-31" to listOf(
            "806463ff-a724-49e1-aae3-182f04ea49ac|چلوکباب|5305042|13500000|16700000|43.23|UNDER",
            "fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6|خورش قیمه|3250002|9000000|10300000|39.72|UNDER",
        ),
        "2026-11-02" to listOf(
            "806463ff-a724-49e1-aae3-182f04ea49ac|چلوکباب|5577338|14500000|17600000|42.31|UNDER",
            "fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6|خورش قیمه|3403065|9000000|10700000|41.59|UNDER",
        ),
        "2026-11-10" to listOf(
            "806463ff-a724-49e1-aae3-182f04ea49ac|چلوکباب|6115691|14500000|19300000|46.39|UNDER",
            "fc9434a9-3c6e-47d7-bfbc-4dfa070f04f6|خورش قیمه|3696712|9000000|11700000|45.18|UNDER",
        ),
    )
}
