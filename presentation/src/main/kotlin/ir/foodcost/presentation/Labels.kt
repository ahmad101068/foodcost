package ir.foodcost.presentation

import ir.foodcost.core.costing.IssueType
import ir.foodcost.core.money.DisplayCurrency
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.model.QtyBasis
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.core.pricing.PricingMethod
import ir.foodcost.core.units.Conversions
import ir.foodcost.core.units.Dimension
import ir.foodcost.core.units.UnitConverter
import ir.foodcost.core.units.Uom
import ir.foodcost.core.model.RecipeVersion
import ir.foodcost.data.ledger.RejectReason
import java.math.BigDecimal

/** Every Persian word the screens show for the core's values. */
object Labels {
    fun currency(currency: DisplayCurrency): String = when (currency) {
        DisplayCurrency.RIAL -> "ریال"
        DisplayCurrency.TOMAN -> "تومان"
        DisplayCurrency.NEW_RIAL -> "ریال جدید"
    }

    fun uom(uom: Uom): String = when (uom) {
        Uom.GRAM -> "گرم"
        Uom.KILOGRAM -> "کیلوگرم"
        Uom.MESGHAL -> "مثقال"
        Uom.MILLILITER -> "میلی‌لیتر"
        Uom.LITER -> "لیتر"
        Uom.TEASPOON -> "قاشق چای‌خوری"
        Uom.TABLESPOON -> "قاشق غذاخوری"
        Uom.PIECE -> "عدد"
        Uom.PORTION -> "پرس"
    }

    /** How an ingredient is measured, as offered when it is created. */
    fun dimension(dimension: Dimension): String = when (dimension) {
        Dimension.MASS -> "وزنی"
        Dimension.VOLUME -> "حجمی"
        Dimension.COUNT -> "تعدادی"
        Dimension.PORTION -> "پرسی"
    }

    /** The unit a price per unit is given for: a kilo, a liter or one piece. */
    fun priceUnit(dimension: Dimension): String = when (dimension) {
        Dimension.MASS -> "کیلو"
        Dimension.VOLUME -> "لیتر"
        Dimension.COUNT -> "عدد"
        Dimension.PORTION -> "پرس"
    }

    fun kind(kind: RecipeKind): String = when (kind) {
        RecipeKind.MENU_ITEM -> "غذای منو"
        RecipeKind.PREP -> "نیمه‌آماده"
        RecipeKind.EVENT_PACKAGE -> "پکیج مجلس"
    }

    fun kindGroup(kind: RecipeKind): String = when (kind) {
        RecipeKind.MENU_ITEM -> "غذاهای منو"
        RecipeKind.PREP -> "نیمه‌آماده‌ها"
        RecipeKind.EVENT_PACKAGE -> "پکیج‌های مجلس"
    }

    fun kindHint(kind: RecipeKind): String = when (kind) {
        RecipeKind.MENU_ITEM -> "غذایی که روی منو فروخته می‌شود."
        RecipeKind.PREP -> "چیزی مثل چلو، سس یا سیخ کباب که داخل غذاهای دیگر می‌رود."
        RecipeKind.EVENT_PACKAGE -> "منوی مجلس که به ازای هر مهمان قیمت می‌خورد."
    }

    fun status(status: PriceStatus): String = when (status) {
        PriceStatus.OK -> "سالم"
        PriceStatus.NEAR -> "نزدیک مرز"
        PriceStatus.UNDER -> "زیر قیمت"
        PriceStatus.NO_PRICE -> "بدون قیمت منو"
        PriceStatus.INCOMPLETE -> "ناقص"
        PriceStatus.EMPTY -> "بدون مواد"
    }

    fun basis(basis: QtyBasis): String = when (basis) {
        QtyBasis.USABLE -> "خالص"
        QtyBasis.AS_PURCHASED -> "خام"
    }

    fun basisHint(basis: QtyBasis): String = when (basis) {
        QtyBasis.USABLE -> "مقداری که بعد از پاک کردن در غذا می‌رود؛ ضایعات خودکار حساب می‌شود."
        QtyBasis.AS_PURCHASED -> "مقدار همان‌طور که خریده شده، با ضایعات."
    }

    fun method(method: PricingMethod): String = when (method) {
        is PricingMethod.TargetFoodCost -> "فودکاست هدف ${Numbers.percent(method.percent)}"
        is PricingMethod.TargetMargin -> "حاشیهٔ سود ${Numbers.percent(method.percent)}"
        is PricingMethod.Markup -> "مارک‌آپ ${Numbers.percent(method.percent)}"
    }

    fun direction(direction: PriceRounding.Direction): String = when (direction) {
        PriceRounding.Direction.UP -> "رو به بالا"
        PriceRounding.Direction.NEAREST -> "نزدیک‌ترین"
        PriceRounding.Direction.DOWN -> "رو به پایین"
    }

    /** What is wrong with a recipe line or a recipe, for the component called [name]. */
    fun issue(type: IssueType, name: String): String = when (type) {
        IssueType.NO_VERSION -> "این دستور در این تاریخ هنوز وجود ندارد."
        IssueType.NO_LINES -> "هنوز ماده‌ای اضافه نشده."
        IssueType.MISSING_COMPONENT -> "«$name» پیدا نشد."
        IssueType.MISSING_QUANTITY -> "مقدار «$name» وارد نشده."
        IssueType.UNIT_MISMATCH -> "واحد «$name» به نوع آن نمی‌خورد؛ وزن هر عدد یا وزن هر میلی‌لیتر آن را ثبت کنید."
        IssueType.NO_PRICE -> "قیمت خرید «$name» ثبت نشده."
        IssueType.PRICE_UNIT_MISMATCH -> "واحد قیمت خرید «$name» به نوع آن نمی‌خورد."
        IssueType.NO_OUTPUT_QUANTITY -> "خروجی کل «$name» ثبت نشده؛ برای برداشتن به وزن لازم است."
        IssueType.CYCLE -> "«$name» از راه دستورهای دیگر به خودش برمی‌گردد."
        IssueType.SUB_RECIPE_INCOMPLETE -> "«$name» هنوز کامل نیست."
    }

    fun rejection(reason: RejectReason): String = when (reason) {
        RejectReason.PERIOD_CLOSED -> "این تاریخ در دورهٔ بسته است و تغییر نمی‌کند."
        RejectReason.NOT_ALLOWED -> "اجازهٔ این کار را ندارید."
        RejectReason.NO_TARGET -> "موردی برای این کار پیدا نشد."
        RejectReason.DUPLICATE -> "این مورد قبلاً ثبت شده است."
        RejectReason.INVALID -> "مقدار وارد شده درست نیست."
    }
}

/** Which units can be offered for what, and the ones a new entry starts with. */
object UnitOptions {
    private val ORDER = listOf(
        Uom.KILOGRAM, Uom.GRAM, Uom.MESGHAL,
        Uom.LITER, Uom.MILLILITER, Uom.TABLESPOON, Uom.TEASPOON,
        Uom.PIECE,
    )

    /** Units an ingredient can be measured in: its own kind first, then those its conversions reach. */
    fun forIngredient(dimension: Dimension, conversions: Conversions): List<Uom> =
        ORDER.filter { UnitConverter.toBase(BigDecimal.ONE, it, dimension, conversions) != null }
            .sortedBy { if (it.dimension == dimension) 0 else 1 }

    /** Units for taking a sub-recipe: portions, and by weight or volume once its output is entered. */
    fun forSubRecipe(version: RecipeVersion?): List<Uom> {
        val output = version?.outputUom ?: return listOf(Uom.PORTION)
        return listOf(Uom.PORTION) + ORDER.filter { it.dimension == output.dimension }
    }

    /** Units a batch's output can be given in. */
    val forOutput: List<Uom> = ORDER

    fun defaultForLine(dimension: Dimension): Uom = when (dimension) {
        Dimension.MASS -> Uom.GRAM
        Dimension.VOLUME -> Uom.MILLILITER
        Dimension.COUNT -> Uom.PIECE
        Dimension.PORTION -> Uom.PORTION
    }

    fun defaultForPurchase(dimension: Dimension): Uom = when (dimension) {
        Dimension.MASS -> Uom.KILOGRAM
        Dimension.VOLUME -> Uom.LITER
        Dimension.COUNT, Dimension.PORTION -> Uom.PIECE
    }
}

/** Prices per kilo, liter or piece. */
object Prices {
    private val THOUSAND = BigDecimal(1000)

    /** Base units (g, ml, piece) in the unit prices are shown for. */
    fun shownUnitSize(dimension: Dimension): BigDecimal =
        if (dimension == Dimension.MASS || dimension == Dimension.VOLUME) THOUSAND else BigDecimal.ONE

    /** هر کیلو ۴۶۰٬۰۰۰ تومان */
    fun perUnit(rialsPerBaseUnit: BigDecimal, dimension: Dimension, money: MoneyFormat): String =
        "هر ${Labels.priceUnit(dimension)} ${money.full(rialsPerBaseUnit.multiply(shownUnitSize(dimension)))}"

    /** ۲۲۰ گرم */
    fun quantity(qty: BigDecimal, uom: Uom): String = "${Numbers.format(qty, 3)} ${Labels.uom(uom)}"
}
