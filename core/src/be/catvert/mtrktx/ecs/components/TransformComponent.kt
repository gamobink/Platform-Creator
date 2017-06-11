package be.catvert.mtrktx.ecs.components

import be.catvert.mtrktx.ecs.systems.physics.GridCell
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Rectangle

/**
 * Created by arno on 03/06/17.
 */

class TransformComponent(val rectangle: Rectangle, val gridCell: MutableList<GridCell> = mutableListOf()) : BaseComponent() {
    override fun copy(target: Entity): BaseComponent {
        return TransformComponent(Rectangle(rectangle), mutableListOf())
    }
}