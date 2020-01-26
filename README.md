# Addict [![](https://jitpack.io/v/Valefant/Addict.svg)](https://jitpack.io/#Valefant/Addict)
This is the name of the Inversion of Control (IoC) container I am developing for learning purposes.
The binding between the interfaces and implementations are done via code.
Injection takes place at the constructor site.

## Example
Here is a basic example showing you how to use the api
```kotlin
interface A
interface B
interface Example

class AImpl : A
class BImpl : B
class ExampleImpl constructor(val a: A, val b: B) : Example

fun main() {
    val container = addict {
        bind<A, AImpl>()
        bind<B, BImpl>()
        bind<Example, ExampleImpl>()
    } 
    val example = container.assemble<Example>() // example is now a valid instantiated object and ready to use
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
The order of the bindings is not important.

The signature of the function to achieve this is the following
```kotlin
fun <I : Any, C> bind(
    kInterface: KClass<I>, 
    kClass: KClass<C>, 
    properties: Map<String, Any> = emptyMap(), 
    scope: Scope = Scope.SINGLETON
) where C : I
``` 
The type parameter ``I`` refers to the interface and can be of ``Any`` type.<br> 
The type parameter ``C`` refers to the implementing class.<br>
The ``where`` statement makes sure that ``<C>`` the class implements only``<I>`` the interface. 

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
The property source is read from the resource folder and is applied in this manner
```kotlin
addict { propertySource("staging.properties") }
```

#### Additions
As known from other frameworks I added the support for string interpolation

Here is a small example
```properties
# Setting a property with key=value
example.name=John
example.surname=Doe

# Interpolation is done via ${key}
example.greet=Hello ${example.name} ${example.surname}
# example.greet=Hello John Doe
```

### Injecting values
```kotlin 
interface Foo
class FooImpl(
    val n: Int,
    val greet: String? = "Hi",
    val pair: Pair<Char, Char>,
    val list: List<Int> = listOf(5, 6, 7, 8)
) : Foo

val props = mapOf(
    "n"     to 42,
    "greet" to properties.getOrElse("example.greet") { "We are doomed!" },
    "pair"  to Pair('a', 'z')
)

container.bind<Foo, FooImpl>(props)
```
The properties for the ``FooImpl`` are provided by the ``props`` map.
Default constructor values don't need to be provided but they can be overwritten if you like.

### Lifecycle
Addict provides an interface [Lifecycle](src/main/kotlin/addict/Lifecycle.kt)
with functions to execute during the lifetime of an object within the container context.

#### PostCreationHook
After the container instantiated a class this function will be executed.
Every property of the class is available within this function context.

### ToDo
- [x] Detecting circular dependencies
- [x] Make property injection and post construct available by code
    - Therefore we are not only depending on annotations. 
    Additionally we can then support default values and other types than strings for the property injection.
- [ ] Provide a better separation of container and modules
- [ ] Add the possibility to lazy load descended dependencies 
- [ ] Add name/tag support