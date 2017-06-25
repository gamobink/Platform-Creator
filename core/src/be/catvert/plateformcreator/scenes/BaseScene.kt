package be.catvert.plateformcreator.scenes

import be.catvert.plateformcreator.MtrGame
import be.catvert.plateformcreator.ecs.EntityEvent
import be.catvert.plateformcreator.ecs.components.RenderComponent
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.app.KtxScreen

/**
 * Created by Catvert on 03/06/17.
 */

/**
 * Classe abstraite permettant de créer une scène
 * game : L'objet du jeu
 * entityEvent : Permet d'implémenter la méthode d'ajout et de suppression d'entité utilisé par les entités
 * systems : Permet de spécifier les systèmes à ajouter à la scène
 */
abstract class BaseScene(protected val _game: MtrGame, protected val _entityEvent: EntityEvent = EntityEvent(), vararg systems: EntitySystem) : KtxScreen {
    val viewport: Viewport
    val stage: Stage

    val camera = OrthographicCamera()

    abstract val entities: MutableSet<Entity>

    val addedSystems = systems

    var background = RenderComponent()

    init {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        viewport = ScreenViewport()
        stage = Stage(viewport, _game.stageBatch)

        Gdx.input.inputProcessor = stage
    }

    override fun render(delta: Float) {
        super.render(delta)

        _game.batch.projectionMatrix = camera.combined

        updateInputs()

        stage.act(delta)
        stage.draw()
    }

    override fun dispose() {
        super.dispose()
        entities.clear()
        stage.dispose()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)

        stage.viewport.update(width, height)
        viewport.update(width, height)
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
    }

    open fun updateInputs() {}
}