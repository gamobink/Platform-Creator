package be.catvert.pc.tweens

import be.catvert.pc.GameObject
import be.catvert.pc.actions.RemoveGOAction
import be.catvert.pc.components.graphics.AtlasComponent
import be.catvert.pc.utility.Updeatable

object TweenSystem : Updeatable {
    private data class TweenData(val tween: Tween, val stateBackupIndex: Int)

    private val tweens = mutableMapOf<GameObject, TweenData>()

    fun startTween(tween: Tween, gameObject: GameObject) {
        tweens[gameObject] = TweenData(tween, gameObject.getCurrentStateIndex())
        tween.init(gameObject)

        if(tween.useTweenState) {
            val atlas = gameObject.getCurrentState().getComponent<AtlasComponent>()
            val state = gameObject.addState("tween-state") {
                if(atlas != null)
                    addComponent(atlas)
            }
            gameObject.setState(state)
        }
    }

    override fun update() {
        val it = tweens.iterator()
        while (it.hasNext()) {
            val keyValue = it.next()

            val gameObject = keyValue.key
            val (tween, backupStateIndex) = keyValue.value

            if (tween.update(gameObject)) {
                val nextTween = tween.nextTween

                if (nextTween != null) {
                    gameObject.setState(backupStateIndex)
                    startTween(tween, gameObject)
                } else {
                    it.remove()

                    if(tween.endAction !is RemoveGOAction) // TODO workaround?
                        gameObject.setState(backupStateIndex)

                    tween.endAction?.invoke(gameObject)
                }
            }
        }
    }
}