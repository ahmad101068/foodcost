package ir.foodcost.presentation

/** Reconcile committed settings without overwriting an unfinished edit on another page. */
fun rebaseSettingsDraft(draft: SettingsInput, previous: SettingsInput, latest: SettingsInput): SettingsInput =
    SettingsPart.entries.fold(latest) { result, part ->
        if (draft.partValue(part) == previous.partValue(part)) result else result.withPart(part, draft)
    }

/** A save acknowledgement is independent of whether Compose observed the intermediate state.
 * Only the submitted group is marked clean, and edits made while saving remain intact.
 */
fun acknowledgeSettingsSave(
    draft: SettingsInput,
    previous: SettingsInput,
    submitted: SettingsInput,
    latest: SettingsInput,
    part: SettingsPart,
): SettingsInput {
    val rebased = rebaseSettingsDraft(draft, previous, latest)
    return if (draft.partValue(part) == submitted.partValue(part)) rebased.withPart(part, latest) else rebased
}

private fun SettingsInput.partValue(part: SettingsPart): Any = when (part) {
    SettingsPart.PRICING -> listOf(method, percent, basis, scope, carried, vatPercent, menuPriceIncludesVat, roundingStepRials, roundingDirection)
    SettingsPart.SECTIONS -> categories.map { it.copy(conversionOrigin = null, originalName = null) }
    SettingsPart.COSTS -> listOf(miscPercent, monthlyFixed, monthlyPortions, onlineCommission, onlineDiscount, onlineUplift)
}

private fun SettingsInput.withPart(part: SettingsPart, source: SettingsInput): SettingsInput = when (part) {
    SettingsPart.PRICING -> copy(method = source.method, percent = source.percent, basis = source.basis,
        scope = source.scope, carried = source.carried, conversionOrigin = source.conversionOrigin,
        vatPercent = source.vatPercent, menuPriceIncludesVat = source.menuPriceIncludesVat,
        roundingStepRials = source.roundingStepRials, roundingDirection = source.roundingDirection)
    SettingsPart.SECTIONS -> copy(categories = source.categories)
    SettingsPart.COSTS -> copy(miscPercent = source.miscPercent, monthlyFixed = source.monthlyFixed,
        monthlyPortions = source.monthlyPortions, onlineCommission = source.onlineCommission,
        onlineDiscount = source.onlineDiscount, onlineUplift = source.onlineUplift)
}
