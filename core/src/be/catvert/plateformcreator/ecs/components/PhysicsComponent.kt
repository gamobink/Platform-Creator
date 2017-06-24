package be.catvert.plateformcreator.ecs.components

import com.badlogic.ashley.core.Entity


/**
 * Created by Catvert on 04/06/17.
 */


enum class MovementType {
    SMOOTH, LINEAR
}

/**
 * Classe de données permettant de gérer les sauts notament en définissant la hauteur du saut
 * jumpHeight : La hauteur du saut
 * isJumping : Permet de savoir si l'entité est entrain de sauté
 * targetHeight : La hauteur en y à atteindre
 * startJumping : Débute le saut de l'entité
 * forceJumping : Permet de forcer le saut de l'entité
 */
data class JumpData(var jumpHeight: Int, var isJumping: Boolean = false, var targetHeight: Int = 0, var startJumping: Boolean = false, var forceJumping: Boolean = false)

/**
 * Enum permettant de connaître le côté toucher lors d'une collision
 */
enum class CollisionSide {
    OnLeft, OnRight, OnUp, OnDown, Unknow;

    operator fun unaryMinus(): CollisionSide {
        if (this == OnLeft)
            return OnRight
        else if (this == OnRight)
            return OnLeft
        else if (this == OnUp)
            return OnDown
        else if (this == OnDown)
            return OnUp
        return Unknow
    }
}

/**
 * Enum permettant de choisir la prochaine "action" physique à appliquer sur l'entité
 */
enum class NextActions {
    GO_LEFT, GO_RIGHT, GO_UP, GO_DOWN, GRAVITY, JUMP
}

enum class MaskCollision {
    ALL, ONLY_PLAYER, ONLY_ENEMY, SENSOR
}

/**
 * Ce component permet d'ajouter à l'entité des propriétés physique tel que la gravité, vitesse de déplacement ...
 * Une entité contenant ce component ne pourra pas être traversé par une autre entité ayant également ce component
 * isStatic : Permet de spécifier si l'entité est sensé bougé ou non
 * moveSpeed : La vitesse de déplacement de l'entité qui sera utilisée avec les NextActions
 * smoothMove : Permet d'inclure ou non le déplacement "fluide" à l'entité
 * gravity : Permet de spécifier si la gravité est appliquée à l'entité
 */
class PhysicsComponent(var isStatic: Boolean, var moveSpeed: Int = 0, var movementType: MovementType = MovementType.LINEAR, var gravity: Boolean = !isStatic, var maskCollision: MaskCollision = MaskCollision.ALL) : BaseComponent<PhysicsComponent>() {
    override fun copy(): PhysicsComponent {
        val physicsComp = PhysicsComponent(isStatic, moveSpeed, movementType, maskCollision = maskCollision)

        physicsComp.jumpData = if (jumpData != null) JumpData(jumpData!!.jumpHeight) else null
        physicsComp.onCollisionWith = onCollisionWith
        physicsComp.onMove = onMove

        return physicsComp
    }

    val nextActions = mutableSetOf<NextActions>()

    var jumpData: JumpData? = null

    /**
     * ActualMoveSpeed permet de connaître la vitesse actuelle de l'entité
     */
    var actualMoveSpeedX = 0f
    var actualMoveSpeedY = 0f

    /**
     * Appelé lorsque une entité ayant ce component touche cette entité
     */
    var onCollisionWith: ((thisEntity: Entity, collisionEntity: Entity, side: CollisionSide) -> Unit)? = null

    /**
     * Appelé lorsque l'entité bouge à cause des NextActions
     */
    var onMove: ((thisEntity: Entity, moveX: Int, moveY: Int) -> Unit)? = null

    /**
     * Permet à l'entité de savoir si elle est sur le sol ou non
     */
    var isOnGround = false
}