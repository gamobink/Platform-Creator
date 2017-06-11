package be.catvert.mtrktx.ecs.systems.physics

import be.catvert.mtrktx.Level
import be.catvert.mtrktx.ecs.components.PhysicsComponent
import be.catvert.mtrktx.ecs.components.TransformComponent
import be.catvert.mtrktx.ecs.systems.BaseSystem
import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle

/**
 * Created by arno on 04/06/17.
 */

class PhysicsSystem(private val level: Level, val gravity: Int = 15) : BaseSystem() {
    private lateinit var entities: ImmutableArray<Entity>

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val physicsMapper = ComponentMapper.getFor(PhysicsComponent::class.java)

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        entities = engine.getEntitiesFor(Family.all(PhysicsComponent::class.java, TransformComponent::class.java).get())

        entities.forEach { entity ->
            val physicsComp = physicsMapper[entity]
            val transformComp = transformMapper[entity]

            if (physicsComp.isStatic || !physicsComp.active)
                return@forEach

            if (physicsComp.gravity && level.applyGravity)
                physicsComp.nextActions += PhysicsComponent.NextActions.GRAVITY

            var moveSpeedX = 0
            var moveSpeedY = 0

            var addJumpAfterClear = false

            physicsComp.nextActions.forEach action@{
                when (it) {
                    PhysicsComponent.NextActions.GO_LEFT -> moveSpeedX -= physicsComp.moveSpeed
                    PhysicsComponent.NextActions.GO_RIGHT -> moveSpeedX += physicsComp.moveSpeed
                    PhysicsComponent.NextActions.GO_UP -> moveSpeedY += physicsComp.moveSpeed
                    PhysicsComponent.NextActions.GO_DOWN -> moveSpeedY -= physicsComp.moveSpeed
                    PhysicsComponent.NextActions.GRAVITY -> if(physicsComp.gravity) moveSpeedY -= gravity
                    PhysicsComponent.NextActions.JUMP -> {

                        if(physicsComp.jumpData == null) {
                            println("L'entité ne contient pas de jumpData")
                            return@action
                        }

                        val jumpData = physicsComp.jumpData!!

                        if(!jumpData.isJumping) {
                            if(!collideOnMove(0, -1, entity))
                                return@action
                            jumpData.isJumping = true
                            jumpData.targetHeight = transformComp.rectangle.y.toInt() + jumpData.jumpHeight
                            jumpData.startJumping = true

                            physicsComp.gravity = false

                            moveSpeedY = gravity
                            addJumpAfterClear = true
                        }
                        else {
                            if(transformComp.rectangle.y >= jumpData.targetHeight || collideOnMove(0, gravity, entity)) {
                                physicsComp.gravity = true
                                jumpData.isJumping = false

                            }
                            else {

                                moveSpeedY = gravity
                                addJumpAfterClear = true
                            }
                            jumpData.startJumping = false
                        }
                    }
                }
            }
            if (physicsComp.smoothMoveData != null) { // Smooth mode
                physicsComp.smoothMoveData.targetMoveSpeedX = moveSpeedX
                physicsComp.smoothMoveData.targetMoveSpeedY = moveSpeedY

                physicsComp.smoothMoveData.actualMoveSpeedX = MathUtils.lerp(physicsComp.smoothMoveData.actualMoveSpeedX, physicsComp.smoothMoveData.targetMoveSpeedX.toFloat(), 0.5f)
                physicsComp.smoothMoveData.actualMoveSpeedY = MathUtils.lerp(physicsComp.smoothMoveData.actualMoveSpeedY, physicsComp.smoothMoveData.targetMoveSpeedY.toFloat(), 0.5f)

                tryMove(physicsComp.smoothMoveData.actualMoveSpeedX.toInt(), physicsComp.smoothMoveData.actualMoveSpeedY.toInt(), entity)
            } else { // NO smooth mode
                tryMove(moveSpeedX, moveSpeedY, entity)
            }

            physicsComp.nextActions.clear()

            if(addJumpAfterClear)
                physicsComp.nextActions += PhysicsComponent.NextActions.JUMP
        }
    }

    private fun tryMove(moveX: Int, moveY: Int, entity: Entity) {
        val transformTarget = transformMapper[entity]

        if (moveX != 0 || moveY != 0) {
            var newMoveX = moveX
            var newMoveY = moveY

            if (!collideOnMove(moveX, 0, entity)) {
                transformTarget.rectangle.x = Math.max(0f, transformTarget.rectangle.x + moveX)
                level.setEntityGrid(entity)
                newMoveX = 0
            }
            if (!collideOnMove(0, moveY, entity)) {
                transformTarget.rectangle.y += moveY
                level.setEntityGrid(entity)
                newMoveY = 0
            }

            if (newMoveX > 0)
                newMoveX -= 1
            else if (newMoveX < 0)
                newMoveX += 1

            if (newMoveY > 0)
                newMoveY -= 1
            else if (newMoveY < 0)
                newMoveY += 1

            tryMove(newMoveX, newMoveY, entity)
        }
    }

    private fun collideOnMove(moveX: Int, moveY: Int, entity: Entity): Boolean {
        val transformTarget = transformMapper[entity]

        val newRect = Rectangle(transformTarget.rectangle)
        newRect.setPosition(newRect.x + moveX, newRect.y + moveY)

        level.getRectCells(newRect).forEach {
           level.matrixGrid[it.x][it.y].first.forEach matrixLoop@ {
                val transformComponent = transformMapper[it]

                if (transformComponent == transformTarget)
                    return@matrixLoop

                if (newRect.overlaps(transformComponent.rectangle))
                    return true
            }
        }

        return false
    }
}