package com.tamawatch.core.model

/** Static content: the shop/food/cosmetic catalog. Pure data. */
object Catalog {
    val meals = listOf(
        Food("meal_bread", FoodKind.MEAL, "Bread", "item_meal_bread", hunger = 30, happy = 2, weight = 3, price = 20),
        Food("meal_bowl", FoodKind.MEAL, "Rice Bowl", "item_meal_bowl", hunger = 45, happy = 4, weight = 4, price = 40),
        Food("meal_fish", FoodKind.MEAL, "Fish", "item_meal_fish", hunger = 50, happy = 6, weight = 3, price = 60),
        Food("meal_veg", FoodKind.MEAL, "Veggies", "item_meal_veg", hunger = 40, happy = 3, weight = 1, price = 30),
    )
    val snacks = listOf(
        Food("snack_cake", FoodKind.SNACK, "Cake", "item_snack_cake", hunger = 5, happy = 30, weight = 6, price = 30),
        Food("snack_candy", FoodKind.SNACK, "Candy", "item_snack_candy", hunger = 2, happy = 22, weight = 4, price = 15),
        Food("snack_juice", FoodKind.SNACK, "Juice", "item_snack_juice", hunger = 8, happy = 18, weight = 2, price = 20),
        Food("snack_icecream", FoodKind.SNACK, "Ice Cream", "item_snack_icecream", hunger = 6, happy = 35, weight = 7, price = 45),
    )
    val allFood = meals + snacks
    fun food(id: String): Food? = allFood.firstOrNull { it.id == id }

    val medicinePrice = 25

    val cosmetics = listOf(
        Cosmetic("cos_beach", "Beach Room", "cos_bg_beach", price = 500),
        Cosmetic("cos_space", "Space Room", "cos_bg_space", price = 800),
    )
    fun cosmetic(id: String): Cosmetic? = cosmetics.firstOrNull { it.id == id }
}
