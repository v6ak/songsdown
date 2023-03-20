import org.scalatest._
import flatspec._
import matchers._
import java.util.{Comparator, Locale}
import java.text.Collator

import com.v6ak.zpevnik.app.Utils.stringComparatorForLocale

class CzechStringComparatorForLocaleTest extends AnyFlatSpec with should.Matchers:

  val sampleCorrectOrder = Seq(
    "auto",
    "cit ron",
    "cit vyslyšet",
    "citron",
    "dáma",
    "chtít",
    "ibišek",
  )

  val sampleCorrectSpacelessOrder = Seq(
    "auto",
    "citron",
    "cit ron",
    "cit vyslyšet",
    "dáma",
    "chtít",
    "ibišek",
  )
  
  val sampleWrongOrder = sampleCorrectOrder.reverse
  
  val locale = Locale.forLanguageTag("cs")

  "sampleWrongOrder" should "be correctly sorted with stringComparatorForLocale" in {
    val ordering = Ordering.comparatorToOrdering(
      stringComparatorForLocale(locale)
    )
    sampleWrongOrder.sorted(ordering) should be (sampleCorrectOrder)
  }

  "sampleWrongOrder" should "be incorrectly sorted natively" in {
    // This is not how Java should behave, it just validates the bug hasn't been fixed,
    // so we still need the hack.
    val ordering = Ordering.comparatorToOrdering(
      Collator.getInstance(locale).asInstanceOf[Comparator[String]]
    )
    sampleWrongOrder.sorted(ordering) should be (sampleCorrectSpacelessOrder)
  }
