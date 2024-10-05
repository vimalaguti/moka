# moka
Macro that creates a `Fields` object inside the companion object of the case class.
The generated `Fields` object contains he name of the defined case class fields.

## Example 

```
@moka
case class Apple(color: String)

Apple.Fields.color == "color"
```

## Usage idea
When using mongodb, you are required to set the field name when filtering or for projections.
With this macro you can avoid setting manually the name and insted use the Fields object.

In the future bson annotations will be supported, so the macro will set the name as defined in the BsonField
