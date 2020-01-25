package addict

import addict.exceptions.CircularDependencyDetectedException
import addict.exceptions.EqualBindingNotAllowedException
import addict.exceptions.NoBindingFoundException
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AddictContainerTest {
    interface Foo
    class FooImpl(
        val n: Int,
        val greet: String? = "Hi",
        val pair: Pair<Char, Char>,
        val list: List<Int> = listOf(5, 6, 7, 8),
        val serviceA: ServiceA
    ) : Foo

    interface Provider {
        fun answerToLife(): Int
    }

    interface ServiceA : Provider
    class ServiceAImpl constructor(val serviceB: ServiceB): ServiceA, Provider {
        override fun answerToLife(): Int {
            return 2 + serviceB.answerToLife()
        }
    }

    class AnotherServiceAImpl : ServiceA {
        override fun answerToLife(): Int {
            return 98
        }
    }

    interface ServiceB : Provider
    class ServiceBImpl constructor(val serviceC: ServiceC): ServiceB {
        override fun answerToLife(): Int {
            return 8 * serviceC.answerToLife()
        }
    }

    class AnotherServiceBImpl : ServiceB {
        override fun answerToLife(): Int {
            return 100
        }
    }

    interface ServiceC : Provider
    class ServiceCImpl : ServiceC {
        override fun answerToLife(): Int {
            return 5
        }
    }

    class AnotherServiceCImpl : ServiceC {
        override fun answerToLife(): Int {
            return 74
        }
    }

    interface Loop
    class LoopImpl constructor(val serviceA: ServiceA, val loop: Loop) : Loop

    interface ServiceExample {
        var test: String
    }

    class AnotherServiceExampleImpl : ServiceExample, Lifecycle {
        override var test: String = ""

        override fun postCreationHook() {
            println("""PostConstruct was successfully executed.
                |Value of member variable ${ServiceExample::test.name}: $test"""
                .trimMargin())

            test = "changed"

            println("Value of member variable ${ServiceExample::test.name}: $test")
        }
    }

    @Test
    fun testDependencyGraph() {
        val container = AddictContainer().apply {
            bind<ServiceA, ServiceAImpl>()
            bind<ServiceB, ServiceBImpl>()
            bind<ServiceC, ServiceCImpl>()
        }
        val serviceA = container.assemble<ServiceA>()
        assertThat(serviceA, instanceOf(ServiceAImpl::class.java))
        val serviceAImpl = container.assemble<ServiceAImpl>()
        assertThat(serviceAImpl, instanceOf(ServiceAImpl::class.java))
        assertThat(serviceAImpl.serviceB, instanceOf(ServiceBImpl::class.java))

        val serviceBImpl = container.assemble<ServiceBImpl>()
        assertThat(serviceBImpl, instanceOf(ServiceBImpl::class.java))
        assertThat(serviceBImpl.serviceC, instanceOf(ServiceCImpl::class.java))
1 to 2
        assertThat(serviceA.answerToLife(), equalTo(42))
    }

    @Test
    fun testModules() {
        val container = AddictContainer().apply {
            bind<ServiceA, ServiceAImpl>()
            bind<ServiceB, ServiceBImpl>()
            bind<ServiceC, AnotherServiceCImpl>()
        }

        val serviceAImpl = container.assemble<ServiceAImpl>()
        assertThat(serviceAImpl, instanceOf(ServiceAImpl::class.java))
        assertThat(serviceAImpl.serviceB, instanceOf(ServiceBImpl::class.java))

        val serviceBImpl = container.assemble<ServiceBImpl>()
        assertThat(serviceBImpl, instanceOf(ServiceBImpl::class.java))
        assertThat(serviceBImpl.serviceC, instanceOf(AnotherServiceCImpl::class.java))

        container.changeModule("newModule")

        container.apply {
            bind<ServiceA, AnotherServiceAImpl>()
            bind<ServiceB, ServiceBImpl>()
        }

        // this exceptions is expected and occurs because no binding for interface ServiceC is set
        assertThrows<NoBindingFoundException> { container.assemble<ServiceAImpl>() }

        assertThat(
            container.assemble<ServiceA>(),
            instanceOf(AnotherServiceAImpl::class.java)
        )
    }

    @Test
    fun testScoping() {
        val container = AddictContainer().apply { bind<ServiceA, AnotherServiceAImpl>() }
        val serviceA = container.assemble<ServiceA>()
        val sameServiceA = container.assemble<ServiceA>()
        assertThat(serviceA, instanceOf(AnotherServiceAImpl::class.java))
        assertThat(sameServiceA, instanceOf(AnotherServiceAImpl::class.java))
        assertThat(serviceA, sameInstance(sameServiceA))

        container.bind<ServiceB, AnotherServiceBImpl>(scope = Scope.NEW_INSTANCE)
        val serviceB = container.assemble<ServiceB>()
        val newServiceB = container.assemble<ServiceB>()
        assertThat(serviceB, instanceOf(AnotherServiceBImpl::class.java))
        assertThat(serviceB, not(sameInstance(newServiceB)))
    }

    @Test
    fun testPropertyInjection() {
        val container = AddictContainer().apply {
            readPropertySource()

            val propertiesToInject = mapOf(
                "n"     to 42,
                "greet" to properties.getOrElse("example.greet") { "We are doomed!" },
                "pair"  to Pair('a', 'z')
            )

            bind<Foo, FooImpl>(propertiesToInject)
            bind<ServiceA, ServiceAImpl>()
            bind<ServiceB, ServiceBImpl>()
            bind<ServiceC, AnotherServiceCImpl>()
        }

        val foo = container.assemble<Foo>()
        assertThat(foo, instanceOf(FooImpl::class.java))

        (foo as FooImpl).also {
            assertThat(it.n, equalTo(42))
            assertThat(it.greet, equalTo("Hello John Doe"))
            assertThat(it.pair, equalTo('a' to 'z'))
            assertThat(it.list, equalTo(listOf(5, 6, 7, 8)))
            assertThat(it.serviceA, instanceOf(ServiceAImpl::class.java))
        }
    }

    @Test
    fun testPropertyInterpolation() {
        val container = AddictContainer().apply { readPropertySource() }
        assertThat(container.properties["example.greet"] as String, Matchers.equalTo("Hello John Doe"))
    }

    @Test
    fun testLifecyclePostCreationHook() {
        val container = AddictContainer().apply { bind<ServiceExample, AnotherServiceExampleImpl>() }
        val serviceExample = container.assemble<ServiceExample>()
        assertThat(serviceExample, instanceOf(AnotherServiceExampleImpl::class.java))
        assertThat(serviceExample.test, Matchers.equalTo("changed"))
    }

    @Test
    fun testEqualBinding() {
        val container = AddictContainer()
        assertThrows<EqualBindingNotAllowedException> { container.bind<ServiceA, ServiceA>() }
    }

    @Test
    fun testDetectingCircularDependencies() {
        val container = AddictContainer().apply {
            bind<ServiceA, ServiceAImpl>()
            bind<ServiceB, ServiceBImpl>()
            bind<ServiceC, ServiceCImpl>()
            bind<Loop, LoopImpl>()
        }

        assertThrows<CircularDependencyDetectedException> { container.assemble<Loop>() }
        assertThrows<CircularDependencyDetectedException> { container.assemble<LoopImpl>() }
    }
}