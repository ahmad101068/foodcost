package ir.foodcost.core.pricing

import ir.foodcost.core.Decimals
import ir.foodcost.core.asFraction
import ir.foodcost.core.costing.RecipeCost
import ir.foodcost.core.model.Recipe
import ir.foodcost.core.model.RecipeKind
import ir.foodcost.core.model.SalesChannel
import ir.foodcost.core.money.PriceRounding
import ir.foodcost.core.over
import java.math.BigDecimal

/** How a suggested price is worked out from the cost. */
sealed interface PricingMethod {
    /** Price = food cost ÷ target share, the usual menu rule. 35 means food is 35% of the net price. */
    data class TargetFoodCost(val percent: BigDecimal) : PricingMethod {
        init {
            require(percent.signum() > 0 && percent < Decimals.HUNDRED) { "The target food cost must be in (0, 100)" }
        }
    }

    /** Price = cost ÷ (1 − margin). 30 means 30% of the net price is left after [basis]. */
    data class TargetMargin(val percent: BigDecimal, val basis: CostBasis = CostBasis.FOOD_COST) : PricingMethod {
        init {
            require(percent.signum() >= 0 && percent < Decimals.HUNDRED) { "The margin must be in [0, 100)" }
        }
    }

    /** Price = cost × (1 + markup). 150 means the price is 2.5 times [basis]. */
    data class Markup(val percent: BigDecimal, val basis: CostBasis = CostBasis.FOOD_COST) : PricingMethod {
        init {
            require(percent.signum() >= 0) { "The markup cannot be negative" }
        }
    }
}

enum class CostBasis {
    /** Ingredients and the misc share only. */
    FOOD_COST,

    /** Food cost plus one portion's share of the overhead. */
    FULL_COST,
}

/**
 * Overhead in its simplest form: the month's fixed costs (wages, rent, energy) spread evenly over
 * the portions sold that month. An estimate; it is shown as such.
 */
data class Overhead(val monthlyFixedRials: Long, val monthlyPortions: BigDecimal) {
    init {
        require(monthlyFixedRials >= 0) { "Fixed costs cannot be negative" }
        require(monthlyPortions.signum() > 0) { "Monthly portions must be positive" }
    }

    val perPortion: BigDecimal get() = BigDecimal(monthlyFixedRials).over(monthlyPortions)
}

/**
 * Selling through a delivery platform: its commission on the net sales after the discount, the
 * discount it runs, and how much dearer the online menu is than the one in the restaurant.
 */
data class OnlineTerms(
    val commissionPercent: BigDecimal = BigDecimal.ZERO,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val priceUpliftPercent: BigDecimal = BigDecimal.ZERO,
) {
    init {
        require(commissionPercent.signum() >= 0 && commissionPercent < Decimals.HUNDRED) { "The commission must be in [0, 100)" }
        require(discountPercent.signum() >= 0 && discountPercent < Decimals.HUNDRED) { "The discount must be in [0, 100)" }
        require(priceUpliftPercent.signum() >= 0) { "The online uplift cannot be negative" }
    }

    /** True when selling online differs from selling at the counter at all. */
    val isSet: Boolean get() = commissionPercent.signum() > 0 || discountPercent.signum() > 0 || priceUpliftPercent.signum() > 0
}

data class PricingSettings(
    val defaultMethod: PricingMethod = PricingMethod.TargetFoodCost(BigDecimal(35)),
    val methodByCategory: Map<String, PricingMethod> = emptyMap(),
    /** VAT in percent. Set it from the tax authority's current notice. */
    val vatPercent: BigDecimal = BigDecimal(10),
    /** True when the prices printed on the menu include VAT. */
    val menuPriceIncludesVat: Boolean = true,
    val rounding: PriceRounding = PriceRounding(100_000, PriceRounding.Direction.UP),
    val overhead: Overhead? = null,
    /** Never suggest a price that leaves less gross profit than this per portion. */
    val minimumGrossProfitRials: Long? = null,
    /** A current price at or above this share of the suggestion is fine. */
    val okRatio: BigDecimal = BigDecimal("0.99"),
    /** At or above this share (and below [okRatio]) the price is near the line; below it, under-priced. */
    val nearRatio: BigDecimal = BigDecimal("0.93"),
    val online: OnlineTerms = OnlineTerms(),
) {
    init {
        require(vatPercent.signum() >= 0 && vatPercent < Decimals.HUNDRED) { "VAT must be in [0, 100)" }
        require(nearRatio.signum() > 0 && nearRatio <= okRatio) { "Need 0 < near ratio ≤ ok ratio" }
        require(minimumGrossProfitRials == null || minimumGrossProfitRials >= 0) { "The minimum profit cannot be negative" }
    }

    /** The item's own rule, else its category's, else the general one. */
    fun methodFor(recipe: Recipe): PricingMethod =
        recipe.pricing ?: recipe.category?.let { methodByCategory[it] } ?: defaultMethod

    /** 1.10 when menu prices include 10% VAT; otherwise 1. */
    val vatFactor: BigDecimal
        get() = if (menuPriceIncludesVat) BigDecimal.ONE + vatPercent.asFraction() else BigDecimal.ONE
}

/**
 * Three measures that answer different questions and are easily confused, so each has its own name.
 * A measure with nothing to divide by is null, never a made-up zero.
 */
object Metrics {
    /** Food cost as a share of net sales. */
    fun foodCostPercent(foodCost: BigDecimal, netSales: BigDecimal): BigDecimal? =
        if (netSales.signum() <= 0) null else foodCost.multiply(Decimals.HUNDRED).over(netSales)

    /** (net sales − cost) ÷ net sales. */
    fun grossMarginPercent(netSales: BigDecimal, cost: BigDecimal): BigDecimal? =
        if (netSales.signum() <= 0) null else (netSales - cost).multiply(Decimals.HUNDRED).over(netSales)

    /** (price − cost) ÷ cost. */
    fun markupPercent(netPrice: BigDecimal, cost: BigDecimal): BigDecimal? =
        if (cost.signum() <= 0) null else (netPrice - cost).multiply(Decimals.HUNDRED).over(cost)
}

data class PriceSuggestion(
    val method: PricingMethod,
    val foodCost: BigDecimal,
    /** The portion's share of the month's fixed costs. */
    val overhead: BigDecimal,
    /** The net price the method asks for, before VAT and rounding. */
    val netPrice: BigDecimal,
    /** The menu price before rounding: [netPrice], plus VAT when menu prices include it. */
    val grossPrice: BigDecimal,
    /** The rounded price to print on the menu. */
    val menuPrice: Long,
    val vatFactor: BigDecimal,
    /** The recipe's own costs that are not food, for a portion eaten in. */
    val extras: BigDecimal = BigDecimal.ZERO,
) {
    /** Food, the recipe's other costs and the overhead share: the base of prices on the full cost. */
    val fullCost: BigDecimal get() = foodCost + extras + overhead
    val menuPriceNet: BigDecimal get() = BigDecimal(menuPrice).over(vatFactor)
    val vat: BigDecimal get() = BigDecimal(menuPrice) - menuPriceNet
    val foodCostPercent: BigDecimal? get() = Metrics.foodCostPercent(foodCost, menuPriceNet)
    val grossProfit: BigDecimal get() = menuPriceNet - foodCost
    val profitAfterOverhead: BigDecimal get() = menuPriceNet - fullCost
}

enum class PriceStatus {
    /** The current price reaches the suggestion. */
    OK,

    /** Slightly below the suggestion. */
    NEAR,

    /** Clearly below the suggestion: the item is losing its margin. */
    UNDER,

    /** No menu price entered. */
    NO_PRICE,

    /** The recipe has issues, so the cost is not reliable. */
    INCOMPLETE,

    /** No recipe lines yet. */
    EMPTY,
}

data class PriceEvaluation(
    val cost: RecipeCost,
    val suggestion: PriceSuggestion,
    val currentPrice: Long?,
    val status: PriceStatus,
) {
    val currentNet: BigDecimal? get() = currentPrice?.let { BigDecimal(it).over(suggestion.vatFactor) }
    val foodCostPercent: BigDecimal? get() = currentNet?.let { Metrics.foodCostPercent(suggestion.foodCost, it) }
    val grossMarginPercent: BigDecimal? get() = currentNet?.let { Metrics.grossMarginPercent(it, suggestion.foodCost) }
    val markupPercent: BigDecimal? get() = currentNet?.let { Metrics.markupPercent(it, suggestion.foodCost) }
    val grossProfit: BigDecimal? get() = currentNet?.let { it - suggestion.foodCost }
    val profitAfterOverhead: BigDecimal? get() = currentNet?.let { it - suggestion.fullCost }

    /** From the current price to the rounded suggestion, in percent of the current price. */
    val changePercent: BigDecimal?
        get() = currentPrice?.let {
            BigDecimal(suggestion.menuPrice - it).multiply(Decimals.HUNDRED).over(BigDecimal(it))
        }
}

class Pricer(private val settings: PricingSettings) {
    fun suggest(cost: RecipeCost): PriceSuggestion {
        val recipe = cost.recipe
        val method = settings.methodFor(recipe)
        val food = cost.foodCostPerPortion
        val overhead = overheadFor(recipe)
        val extras = cost.extrasPerPortion(SalesChannel.DINE_IN)
        fun base(basis: CostBasis) = if (basis == CostBasis.FULL_COST) food + extras + overhead else food

        var net = when (method) {
            is PricingMethod.TargetFoodCost -> food.over(method.percent.asFraction())
            is PricingMethod.TargetMargin -> base(method.basis).over(BigDecimal.ONE - method.percent.asFraction())
            is PricingMethod.Markup -> base(method.basis).multiply(BigDecimal.ONE + method.percent.asFraction())
        }
        settings.minimumGrossProfitRials?.let { net = net.max(food + BigDecimal(it)) }
        val vatFactor = settings.vatFactor
        val gross = net.multiply(vatFactor)
        return PriceSuggestion(method, food, overhead, net, gross, settings.rounding.apply(gross), vatFactor, extras)
    }

    private fun overheadFor(recipe: Recipe): BigDecimal =
        if (recipe.kind == RecipeKind.PREP) BigDecimal.ZERO else settings.overhead?.perPortion ?: BigDecimal.ZERO

    /** The recipe's own other costs for [channel] and its share of the overhead: everything but food and commission. */
    fun otherCosts(cost: RecipeCost, channel: SalesChannel): BigDecimal = cost.extrasPerPortion(channel) + overheadFor(cost.recipe)

    /** The price of [channel] when the menu in the restaurant says [menuPrice]: online prices carry the uplift. */
    fun channelPrice(menuPrice: Long, channel: SalesChannel): Long {
        val uplift = settings.online.priceUpliftPercent
        if (channel != SalesChannel.ONLINE || uplift.signum() == 0) return menuPrice
        return settings.rounding.apply(BigDecimal(menuPrice).multiply(BigDecimal.ONE + uplift.asFraction()))
    }

    /** One portion of [cost] sold through [channel] at [price], the price the customer sees on that menu. */
    fun sale(cost: RecipeCost, channel: SalesChannel, price: Long): Sale {
        val online = channel == SalesChannel.ONLINE
        val gross = BigDecimal(price)
        val discount = if (online) gross.multiply(settings.online.discountPercent.asFraction()) else BigDecimal.ZERO
        val net = (gross - discount).over(settings.vatFactor)
        val commission = if (online) net.multiply(settings.online.commissionPercent.asFraction()) else BigDecimal.ZERO
        return Sale(channel, price, discount, net, commission, cost.foodCostPerPortion, otherCosts(cost, channel))
    }

    /**
     * The menu price at which [channel] leaves [remaining] per portion, rounded up like menu prices:
     * what to charge online so that, after the discount and commission, a portion leaves as much as
     * it does in the restaurant. Null when nothing can.
     */
    fun priceLeaving(remaining: BigDecimal, cost: RecipeCost, channel: SalesChannel): Long? {
        val online = channel == SalesChannel.ONLINE
        val kept = BigDecimal.ONE - if (online) settings.online.commissionPercent.asFraction() else BigDecimal.ZERO
        val paid = BigDecimal.ONE - if (online) settings.online.discountPercent.asFraction() else BigDecimal.ZERO
        val net = (remaining + cost.foodCostPerPortion + otherCosts(cost, channel)).over(kept)
        if (net.signum() <= 0) return null
        return settings.rounding.apply(net.multiply(settings.vatFactor).over(paid))
    }

    /**
     * Pricing backwards: the most a portion may cost for [menuPrice] to still meet the item's rule,
     * on the rule's own basis: the food alone for a food cost target, or food and other costs for a
     * margin or markup on the full cost.
     */
    fun maxCost(recipe: Recipe, menuPrice: Long): MaxCost {
        val net = BigDecimal(menuPrice).over(settings.vatFactor)
        return when (val m = settings.methodFor(recipe)) {
            is PricingMethod.TargetFoodCost -> MaxCost(net.multiply(m.percent.asFraction()), CostBasis.FOOD_COST)
            is PricingMethod.TargetMargin -> MaxCost(net.multiply(BigDecimal.ONE - m.percent.asFraction()), m.basis)
            is PricingMethod.Markup -> MaxCost(net.over(BigDecimal.ONE + m.percent.asFraction()), m.basis)
        }
    }

    fun evaluate(cost: RecipeCost, currentPrice: Long?): PriceEvaluation {
        val suggestion = suggest(cost)
        val status = when {
            !cost.hasLines -> PriceStatus.EMPTY
            !cost.isComplete -> PriceStatus.INCOMPLETE
            currentPrice == null -> PriceStatus.NO_PRICE
            suggestion.grossPrice.signum() <= 0 -> PriceStatus.OK
            else -> {
                val ratio = BigDecimal(currentPrice).over(suggestion.grossPrice)
                when {
                    ratio >= settings.okRatio -> PriceStatus.OK
                    ratio >= settings.nearRatio -> PriceStatus.NEAR
                    else -> PriceStatus.UNDER
                }
            }
        }
        return PriceEvaluation(cost, suggestion, currentPrice, status)
    }
}

/** The most a portion may cost for its menu price to meet the pricing rule, and what that cost covers. */
data class MaxCost(val amount: BigDecimal, val basis: CostBasis)

/**
 * One portion sold through [channel] at [price], as printed on that menu: what reaches the
 * restaurant after the discount, VAT and the platform's commission, what the portion costs, and
 * what is left. What is left is not profit: only the costs that were entered are taken off.
 */
data class Sale(
    val channel: SalesChannel,
    val price: Long,
    /** Taken off the price before the customer pays. */
    val discount: BigDecimal,
    /** What the customer pays, without VAT. */
    val netSales: BigDecimal,
    /** The platform's share of [netSales]. */
    val commission: BigDecimal,
    val foodCost: BigDecimal,
    /** The recipe's other costs for this channel and its overhead share. */
    val otherCosts: BigDecimal,
) {
    /** Every cost entered: food, other costs and commission. */
    val registeredCost: BigDecimal get() = foodCost + otherCosts + commission

    val remaining: BigDecimal get() = netSales - registeredCost

    /** Food alone as a share of the net sales. */
    val foodCostPercent: BigDecimal? get() = Metrics.foodCostPercent(foodCost, netSales)

    /** On every cost entered. */
    val marginPercent: BigDecimal? get() = Metrics.grossMarginPercent(netSales, registeredCost)

    /** On every cost entered. */
    val markupPercent: BigDecimal? get() = Metrics.markupPercent(netSales, registeredCost)
}
