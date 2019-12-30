package addict

import addict.exceptions.CircularDependencyDetectedException
import addict.exceptions.NoBindingFoundException
import addict.exceptions.PropertyDoesNotExistException
import addict.exceptions.SameBindingNotAllowedException
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
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

    interface Loop
    class LoopImpl @Inject constructor(val serviceA: ServiceA, val loop: Loop) : Loop

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

    class DoomedServiceExampleImpl : ServiceExample {
        @Value("this.does.not.exist")
        override lateinit var test: String
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
        assertThat(serviceA, CoreMatchers.instanceOf(ServiceAImpl::class.java))

        val serviceAImpl = container.assemble<ServiceAImpl>()
        assertThat(serviceAImpl, CoreMatchers.instanceOf(ServiceAImpl::class.java))
        assertThat(serviceAImpl.serviceB, CoreMatchers.instanceOf(ServiceBImpl::class.java))

        val serviceBImpl = container.assemble<ServiceBImpl>()
        assertThat(serviceBImpl, CoreMatchers.instanceOf(ServiceBImpl::class.java))
        assertThat(serviceBImpl.serviceC, CoreMatchers.instanceOf(AnotherServiceCImpl::class.java))
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
        assertThat(serviceAImpl, CoreMatchers.instanceOf(ServiceAImpl::class.java))
        assertThat(serviceAImpl.serviceB, CoreMatchers.instanceOf(ServiceBImpl::class.java))

        val serviceBImpl = container.assemble<ServiceBImpl>()
        assertThat(serviceBImpl, CoreMatchers.instanceOf(ServiceBImpl::class.java))
        assertThat(serviceBImpl.serviceC, CoreMatchers.instanceOf(AnotherServiceCImpl::class.java))

        container.changeModule("newModule")

        container.apply {
            bind(ServiceA::class, AnotherServiceAImpl::class)
            bind(ServiceB::class, AnotherServiceBImpl::class)
            bind(ServiceC::class, ServiceCImpl::class)
        }

        assertThrows<NoBindingFoundException> { container.assemble<ServiceAImpl>() }

        assertThat(
            container.assemble<ServiceA>(),
            CoreMatchers.instanceOf(AnotherServiceAImpl::class.java))

        assertThat(
            container.assemble<ServiceB>(),
            CoreMatchers.instanceOf(AnotherServiceBImpl::class.java))
    }

    @Test
    fun testScoping() {
        val container = AddictContainer()
        container.bind(ServiceA::class, AnotherServiceAImpl::class)
        val serviceA = container.assemble<ServiceA>()
        val sameServiceA = container.assemble<ServiceA>()
        assertThat(serviceA, CoreMatchers.instanceOf(AnotherServiceAImpl::class.java))
        assertThat(sameServiceA, CoreMatchers.instanceOf(AnotherServiceAImpl::class.java))
        assertThat(serviceA, CoreMatchers.sameInstance(sameServiceA))

        container.bind(ServiceB::class, AnotherServiceBImpl::class, Scope.NEW_INSTANCE)
        val serviceB = container.assemble<ServiceB>()
        val newServiceB = container.assemble<ServiceB>()
        assertThat(serviceB, CoreMatchers.instanceOf(AnotherServiceBImpl::class.java))
        assertThat(serviceB, CoreMatchers.not(CoreMatchers.sameInstance(newServiceB)))
    }

    @Test
    fun testValueAnnotation() {
        val container = AddictContainer().apply { propertySource("application.properties") }
        container.apply {
            bind(ServiceExample::class, ServiceExampleImpl::class)
        }
        val serviceExample = container.assemble<ServiceExample>()
        assertThat(serviceExample, CoreMatchers.instanceOf(ServiceExampleImpl::class.java))
        assertThat(serviceExample.test, Matchers.equalTo("Hello World!"))
    }

    @Test
    fun testPropertyInterpolation() {
        val container = AddictContainer().apply { propertySource("application.properties") }
        assertThat(container.properties["example.greet"] as String, Matchers.equalTo("Hello John Doe"))
    }

    @Test
    fun testPropertyDoesNotExist() {
        val container = AddictContainer().apply {
            propertySource("application.properties")
            bind(ServiceExample::class, DoomedServiceExampleImpl::class)
        }

        assertThrows<PropertyDoesNotExistException> {
            container.assemble<ServiceExample>()
        }
    }

    @Test
    fun testPostCreationAnnotation() {
        val container = AddictContainer()
        container.apply {
            bind(ServiceExample::class, AnotherServiceExampleImpl::class)
        }
        val serviceExample = container.assemble<ServiceExample>()
        assertThat(serviceExample, CoreMatchers.instanceOf(AnotherServiceExampleImpl::class.java))
        assertThat(serviceExample.test, Matchers.equalTo("changed"))
    }

    @Test
    fun testEqualBinding() {
        val container = AddictContainer()
        assertThrows<SameBindingNotAllowedException> { container.bind(ServiceA::class, ServiceA::class) }
    }

    @Test
    fun testDetectingCircularDependencies() {
        val container = AddictContainer()
        container.apply {
            bind(ServiceA::class, ServiceAImpl::class)
            bind(ServiceB::class, ServiceBImpl::class)
            bind(ServiceC::class, ServiceCImpl::class)
            bind(Loop::class, LoopImpl::class)
        }

        assertThrows<CircularDependencyDetectedException> { container.assemble<Loop>() }
        assertThrows<CircularDependencyDetectedException> { container.assemble<LoopImpl>() }
    }
}