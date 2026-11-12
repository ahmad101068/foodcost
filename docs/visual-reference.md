# Approved FoodCost visual reference

The approved calculator image uses a graphical dashboard above a compact form, a shared three-column ingredient table, and a full selected navigation tile. The other approved image applies the same forest-green controls and outlined icons to materials, dishes, settings, pricing and exit confirmation.

At a 411dp phone width, the calculator reference has roughly a 259dp dashboard, 171dp form and 220dp ingredient card for four ingredients. Editable wells use a thin cool-gray outline, 9dp corners and bold values; card spacing is 8–10dp. The normal layout grows or stacks with larger text rather than fixing the whole screen's height.

| Element | Light color |
| --- | --- |
| Main controls | `#005747` |
| Page / card | `#FAF9F6` / `#FFFFFF` |
| Borders / main text | `#CBD0D9` / `#111313` |
| Sales | `#1233AB` on `#F0F2FF` |
| Food cost | `#EE6105` on `#FFF1E3` |
| Positive remaining | `#087828` on `#E5F7E3` |
| Suitable price | `#086C23` on `#E5F6E1` |
| Near target | `#805615` on `#FFF3DC` |
| Underpriced | `#E62625` on `#FFDEDC` |

Colors are explicit theme tokens; dark mode has separate legible colors. Pricing statuses always come from the pricing model, including unpriced and incomplete records.

`ReferenceVisualTest` opens real Compose screens with the reference's four-ingredient dish and takes native Android screenshots. It requires the fourth ingredient to be visible without scrolling, captures materials/dishes/settings/pricing/exit, and leaves the images in the visual-preview workflow artifact. Compare these actual images against the approved source at equal width without recoloring or changing aspect ratio.

The proposal's sales box mixed a VAT-inclusive sale with a net-sale food-cost percentage. The implementation keeps the correct label and values: 630,000 toman menu price, 572,727 net sale, 364,105 ingredients, 63.6% food cost and 208,622 remaining. No financial values are changed to imitate illustrative artwork.
