# Moka Macro

## Installation
To install
```scala
libraryDependencies += "io.vimalaguti" %% "moka" % "@VERSION@"
```

## Usage

```scala
@moka
case class Apple(color: String)

Apple.Fields.color == "color"
```