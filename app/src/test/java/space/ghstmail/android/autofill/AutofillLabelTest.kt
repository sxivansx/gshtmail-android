package space.ghstmail.android.autofill

import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillLabelTest {

    @Test
    fun `a website is labelled by its domain`() {
        assertEquals("reddit.com", aliasLabel("www.Reddit.com", "Chrome", "com.android.chrome"))
    }

    @Test
    fun `an app is labelled by its name`() {
        assertEquals("Pinterest", aliasLabel(null, "Pinterest", "com.pinterest"))
    }

    @Test
    fun `an app the system will not name is labelled from its package`() {
        assertEquals("Pinterest", aliasLabel(null, null, "com.pinterest"))
        assertEquals("Instagram", aliasLabel("", " ", "com.instagram.android"))
        assertEquals("Recyklo", aliasLabel(null, null, "in.recyklo.partner.staging"))
    }

    @Test
    fun `a name that is only the package is not used`() {
        assertEquals("Pinterest", aliasLabel(null, "com.pinterest", "com.pinterest"))
    }

    @Test
    fun `a package of nothing but generic parts keeps its last part`() {
        assertEquals("App", nameFromPackage("com.app"))
        assertEquals("", nameFromPackage(""))
    }

    @Test
    fun `a long name is cut`() {
        assertEquals(40, aliasLabel(null, "A".repeat(60), "com.example").length)
    }
}
