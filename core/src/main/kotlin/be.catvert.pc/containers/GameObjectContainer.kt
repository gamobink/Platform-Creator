package be.catvert.pc.containers

import be.catvert.pc.GameObject
import be.catvert.pc.GameObjectTag
import be.catvert.pc.serialization.PostDeserialization
import be.catvert.pc.utility.Renderable
import be.catvert.pc.utility.ResourceLoader
import be.catvert.pc.utility.Updeatable
import com.badlogic.gdx.graphics.g2d.Batch
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

abstract class GameObjectContainer : Renderable, Updeatable, PostDeserialization, ResourceLoader {
    @JsonProperty("objects")
    protected val gameObjects: MutableSet<GameObject> = mutableSetOf()

    protected open val processGameObjects: Set<GameObject>
        get() = gameObjects

    @JsonIgnore
    var allowRenderingGO = true
    @JsonIgnore
    var allowUpdatingGO = true

    private val removeGameObjects = mutableSetOf<GameObject>()

    @JsonIgnore
    fun getGameObjectsData() = gameObjects.toSet()

    open fun findGameObjectsByTag(tag: GameObjectTag): Set<GameObject> = gameObjects.filter { it.tag == tag }.toSet()

    open fun removeGameObject(gameObject: GameObject) {
        removeGameObjects.add(gameObject)
    }

    open fun addGameObject(gameObject: GameObject): GameObject {
        gameObject.loadResources()

        gameObjects.add(gameObject)
        gameObject.container = this

        return gameObject
    }

    protected open fun onRemoveGameObject(gameObject: GameObject) {}

    override fun loadResources() {
        gameObjects.forEach {
            it.loadResources()
        }
    }

    override fun render(batch: Batch) {
        if (allowRenderingGO)
            processGameObjects.sortedBy { it.layer }.forEach { it.render(batch) }
    }

    override fun update() {
        processGameObjects.forEach {
            if (allowUpdatingGO)
                it.update()

            if (it.position().y < 0) {
                it.onOutOfMapAction(it)
            }
        }

        removeGameObjects()
    }

    private fun removeGameObjects() {
        if (removeGameObjects.isNotEmpty()) {
            removeGameObjects.forEach {
                onRemoveGameObject(it)
                gameObjects.remove(it)
                it.container = null
            }
            removeGameObjects.clear()
        }
    }

    override fun onPostDeserialization() {
        gameObjects.forEach {
            it.container = this
            it.onRemoveFromParent.register {
                removeGameObjects.add(it)
            }
        }

        loadResources()
    }

    operator fun plusAssign(gameObject: GameObject) {
        addGameObject(gameObject)
    }
}
