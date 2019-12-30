# Addict [![](https://jitpack.io/v/Valefant/Addict.svg)](https://jitpack.io/#Valefant/Addict)
This is the name of the Inversion of Control (IoC) container I am developing for learning purposes.
The binding between the the interfaces and implementation is done via code.
By using the javax ``@Inject`` annotation on the constructor the container starts building the dependency graph.

## Example
A basic example showing how to use it.
```kotlin
import javax.inject.Inject

interface A
interface B
interface Example

class AImpl : A
class BImpl : B
class ExampleImpl @Inject constructor(val a: A, val b: B): Example

fun main() {
    val container = AddictContainer().apply {
        bind(A::class, AImpl::class)
        bind(B::class, BImpl::class)
        bind(Example::class, ExampleImpl::class)
    }
    val example = container.assemble<Example>()
}
```
For further examples you can have a look at the tests.

## Modules
Addict supports modules to provide a separation of the context you are working on.
When instantiating the container a default module is provided.
Changing a module is straightforward 
```kotlin
container.changeModule("newModule")
```
When the module does not exist it will be created automatically.

### Binding
Interfaces and implementations are bound programmatically.
The signature of the function to do this is the following
```kotlin
fun <I : Any> bind(kInterface: KClass<I>, kClass: KClass<out I>, scope: Scope = Scope.SINGLETON)
``` 
The type parameter ``I`` can be any class. 
Only interfaces can be bound to classes as ``<out I>`` will only accept supertypes of ``<I>`` 
which is therefore implementing the interface.

#### Scoping
By default the requested instances are Singletons.
If you want to request different instances of the same type 
make sure to pass the ``Scope.NEW_INSTANCE`` as last parameter to the bind function.

## Assemble 
The function signature to assemble a dependency from the container environment looks like this
```kotlin
inline fun <reified T : Any> assemble(): T
```
In Java there is only the possibility to give the Class as parameter to retain the information during the runtime.
The api would therefore be used in this way ``assemble(Example.class)``.
The keyword ``reified`` is new to Kotlin and allows us to use the api in a C# like way ``assemble<Example>()``.

If you prefer the Java way you can use still use the function in this way ``assemble(Example::class)``

### Java Properties Source
The property source is read from the resource folder and is applied in this way
```kotlin
val container = AddictContainer().apply { propertySource("application.properties") }
```
A ``@Value`` annotation is provided for injecting values during the object creation
as you may know it from projects like Spring.

#### Additions
As known from other frameworks I added support for string interpolation

Here is a small example
```properties
# Comment

# Setting a property with key=value
example.hello=Hello

# Interpolation is done via ${name}
example.greet=${example.hello} John!
```

### Life cycle
``@PostConstruct`` annotation is supported.
After the container instantiated a class, the annotated function is executed.
Every property is available within the function context.

### ToDo
- [x] Detecting circular dependencies
- [ ] Make property injection and post construct available by code``
    - Therefore we are not only depending on annotations. 
    Additionally we can then support default values and other types than strings for the property injection. 
    