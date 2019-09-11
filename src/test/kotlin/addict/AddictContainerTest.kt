package addict

import addict.exceptions.NoBindingFoundException
import addict.exceptions.SameBindingNotAllowedException
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.annotation.PostConstruct
import javax.inject.Inject

class AddictContainerTest {

    interface ServiceA
    class ServiceAImpl @Inject constructor(val serviceB: ServiceB): ServiceA
    class AnotherServiceAImpl : ServiceA

    interface ServiceB
    class ServiceBImpl @Inject constructor(val serviceC: ServiceC): ServiceB
    class AnotherServiceBImpl : ServiceB

    interface ServiceC
    class ServiceCImpl : ServiceC
    class AnotherServiceCImpl : ServiceC

    interface ServiceExample {
        var test: String
    }

    class ServiceExampleImpl : ServiceExample {
        @Value("example.text")
        override lateinit var test: String
    }

    class AnotherServiceExampleImpl : ServiceExample {
        override var test: String = ""

        @PostConstruct
        fun afterInitialization() {
            println("""PostConstruct was successfully executed.
                |Value of member variable ${ServiceExampleImpl::test.name}: $test"""
                .trimMargin())

            test = "changed"

            println("Value of member variable ${ServiceExampleImpl::test.name}: $test")
        }
    }

    @Test
    fun testDependencyGraph() {
        val container = AddictContainer()
        container.apply {
            bind(ServiceA::class, ServiceAImpl::class)
            bind(ServiceB::class, ServiceBImpl::class)
            bind(ServiceC::class, AnotherServiceCImpl::class)
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
        val container = AddictContainer()
        container.apply {
            bind(ServiceA::class, ServiceAImpl::class)
            bind(ServiceB::class, ServiceBImpl::class)
            bind(ServiceC::class, AnotherServiceCImpl::class)
        }

        val serviceAImpl = container.assemble<ServiceAImpl>()
        MatcherAssert.assertThat(serviceAImpl, CoreMatchers.instanceOf(ServiceAImpl::class.java))
        MatcherAssert.assertThat(serviceAImpl.serviceB, CoreMatchers.instanceOf(ServiceBImpl::class.java))

        val serviceBImpl = container.assemble<ServiceBImpl>()
        MatcherAssert.assertThat(serviceBImpl, CoreMatchers.instanceOf(ServiceBImpl::class.java))
        MatcherAssert.assertThat(serviceBImpl.serviceC, CoreMatchers.instanceOf(AnotherServiceCImpl::class.java))

        container.changeModule("newModule")

        container.apply {
            bind(ServiceA::class, AnotherServiceAImpl::class)
            bind(ServiceB::class, AnotherServiceBImpl::class)
            bind(ServiceC::class, ServiceCImpl::class)
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
    fun testScoping() {
        val container = AddictContainer().apply { propertySource("application.properties") }
        container.bind(ServiceA::class, AnotherServiceAImpl::class)
        val serviceA = container.assemble<ServiceA>()
        val sameServiceA = container.assemble<ServiceA>()
        MatcherAssert.assertThat(serviceA, CoreMatchers.instanceOf(AnotherServiceAImpl::class.java))
        MatcherAssert.assertThat(sameServiceA, CoreMatchers.instanceOf(AnotherServiceAImpl::class.java))
        MatcherAssert.assertThat(serviceA, CoreMatchers.sameInstance(sameServiceA))

        container.bind(ServiceB::class, AnotherServiceBImpl::class, Scope.NEW_INSTANCE)
        val serviceB = container.assemble<ServiceB>()
        val newServiceB = container.assemble<ServiceB>()
        MatcherAssert.assertThat(serviceB, CoreMatchers.instanceOf(AnotherServiceBImpl::class.java))
        MatcherAssert.assertThat(serviceB, CoreMatchers.not(CoreMatchers.sameInstance(newServiceB)))
    }

    @Test
    fun testValueAnnotation() {
        val container = AddictContainer().apply { propertySource("application.properties") }
        container.apply {
            bind(ServiceExample::class, ServiceExampleImpl::class)
        }
        val serviceExample = container.assemble<ServiceExample>()
        MatcherAssert.assertThat(serviceExample, CoreMatchers.instanceOf(ServiceExampleImpl::class.java))
        MatcherAssert.assertThat(serviceExample.test, Matchers.equalTo("hello world!"))
    }

    @Test
    fun testPostCreationAnnotation() {
        val container = AddictContainer().apply { propertySource("application.properties") }
        container.apply {
            bind(ServiceExample::class, AnotherServiceExampleImpl::class)
        }
        val serviceExample = container.assemble<ServiceExample>()
        MatcherAssert.assertThat(serviceExample, CoreMatchers.instanceOf(AnotherServiceExampleImpl::class.java))
        MatcherAssert.assertThat(serviceExample.test, Matchers.equalTo("changed"))
    }

    @Test
    fun testSameBinding() {
        val container = AddictContainer()
        assertThrows<SameBindingNotAllowedException> { container.bind(ServiceA::class, ServiceA::class) }
    }

    @Test
    fun testDetectingCircularDependencies() {

    }
}