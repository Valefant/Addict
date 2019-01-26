import addict.Addict
import addict.NoBindingFoundException
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject

class AddictTest {

    interface ServiceA
    class ServiceAImpl @Inject constructor(val serviceB: ServiceB): ServiceA
    class AnotherServiceAImpl : ServiceA

    interface ServiceB
    class ServiceBImpl @Inject constructor(val serviceC: ServiceC): ServiceB
    class AnotherServiceBImpl : ServiceB

    interface ServiceC
    class ServiceCImpl : ServiceC
    class AnotherServiceCImpl : ServiceC

    @Test
    fun testSimpleDependencyGraph() {
        val container = Addict()
        container.apply {
            bind(ServiceA::class.java, ServiceAImpl::class.java)
            bind(ServiceB::class.java, ServiceBImpl::class.java)
            bind(ServiceC::class.java, AnotherServiceCImpl::class.java)
        }
        val serviceA = container.assemble<ServiceA>()
        MatcherAssert.assertThat(serviceA, CoreMatchers.instanceOf(ServiceAImpl::class.java))

        val serviceAImpl = container.assemble<ServiceAImpl>()
        MatcherAssert.assertThat(serviceAImpl, CoreMatchers.instanceOf(ServiceAImpl::class.java))
        MatcherAssert.assertThat(serviceAImpl.serviceB, CoreMatchers.instanceOf(ServiceBImpl::class.java))

        val serviceBImpl = container.assemble<ServiceBImpl>()
        MatcherAssert.assertThat(serviceBImpl, CoreMatchers.instanceOf(ServiceBImpl::class.java))
        MatcherAssert.assertThat(serviceBImpl.serviceC, CoreMatchers.instanceOf(AnotherServiceCImpl::class.java))
    }

    @Test
    fun testModules() {
        val container = Addict()
        container.apply {
            bind(ServiceA::class.java, ServiceAImpl::class.java)
            bind(ServiceB::class.java, ServiceBImpl::class.java)
            bind(ServiceC::class.java, AnotherServiceCImpl::class.java)
        }

        val serviceAImpl = container.assemble<ServiceAImpl>()
        MatcherAssert.assertThat(serviceAImpl, CoreMatchers.instanceOf(ServiceAImpl::class.java))
        MatcherAssert.assertThat(serviceAImpl.serviceB, CoreMatchers.instanceOf(ServiceBImpl::class.java))

        val serviceBImpl = container.assemble<ServiceBImpl>()
        MatcherAssert.assertThat(serviceBImpl, CoreMatchers.instanceOf(ServiceBImpl::class.java))
        MatcherAssert.assertThat(serviceBImpl.serviceC, CoreMatchers.instanceOf(AnotherServiceCImpl::class.java))

        container.changeModule("newModule")

        container.apply {
            bind(ServiceA::class.java, AnotherServiceAImpl::class.java)
            bind(ServiceB::class.java, AnotherServiceBImpl::class.java)
            bind(ServiceC::class.java, ServiceCImpl::class.java)
        }

        assertThrows<NoBindingFoundException> { container.assemble<ServiceAImpl>() }

        MatcherAssert.assertThat(
            container.assemble<ServiceA>(),
            CoreMatchers.instanceOf(AnotherServiceAImpl::class.java))

        MatcherAssert.assertThat(
            container.assemble<ServiceB>(),
            CoreMatchers.instanceOf(AnotherServiceBImpl::class.java))
    }

    @Test
    fun testComplexDependencyGraph() {

    }

    @Test
    fun testValueInjection() {

    }

    @Test
    fun testScope() {

    }

    @Test
    fun postCreationHook() {

    }
}