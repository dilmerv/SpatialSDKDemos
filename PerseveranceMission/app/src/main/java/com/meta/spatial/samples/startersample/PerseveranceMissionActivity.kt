/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.spatial.samples.startersample

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.runtime.NetworkedAssetLoader
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.toolkit.Animated
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PlaybackState
import com.meta.spatial.toolkit.PlaybackType
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PerseveranceMissionActivity : AppSystemActivity() {
  private var gltfxEntity: Entity? = null
  private val activityScope = CoroutineScope(Dispatchers.Main)

  private var mars: Entity? = null
  private var perseverance: Entity? = null
  private var environment: Entity? = null
  private var skybox: Entity? = null

  private var marsVisibility: Boolean = false
  private var perseveranceVisibility: Boolean = false
  private var nightModeVisibility: Boolean = false
  private var mrModeOn: Boolean = false

  override fun registerFeatures(): List<SpatialFeature> {
    val features = mutableListOf<SpatialFeature>(VRFeature(this))
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
    }
    return features
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NetworkedAssetLoader.init(
      File(applicationContext.getCacheDir().canonicalPath), OkHttpAssetFetcher()
    )

    // wait for GLXF to load before accessing nodes inside it
    loadGLXF().invokeOnCompletion {
      // get the environment mesh from Cosmo and set it to use an unlit shader.
      val composition = glXFManager.getGLXFInfo("example_key_name")
      environment = composition.getNodeByName("Environment").entity
      mars = composition.getNodeByName("Mars").entity
      mars?.setComponent(Grabbable())
      perseverance = composition.getNodeByName("Perseverance").entity

      val environmentMesh = environment?.getComponent<Mesh>()
      environmentMesh?.defaultShaderOverride = SceneMaterial.UNLIT_SHADER
      environment?.setComponent(environmentMesh!!)
    }

    // component & system registrations
    componentManager.registerComponent<Rotator>(Rotator.Companion)
    systemManager.registerSystem(RotatorSystem())
  }

  override fun onSceneReady() {
    super.onSceneReady()

    // set the reference space to enable recentering
    scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)

    scene.setLightingEnvironment(
      ambientColor = Vector3(0f),
      sunColor = Vector3(7.0f, 7.0f, 7.0f),
      sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
      environmentIntensity = 0.3f
    )
    scene.updateIBLEnvironment("environment.env")

    scene.setViewOrigin(0.0f, 0.0f, 2.0f, 180.0f)

    skybox = Entity.create(
      listOf(
        Mesh(Uri.parse("mesh://skybox")),
        Material().apply {
          baseTextureAndroidResourceId = R.drawable.skydome
          unlit = true // Prevent scene lighting from affecting the skybox
        },
        Transform(Pose(Vector3(x = 0f, y = 0f, z = 0f)))
      )
    )
  }

  override fun registerPanels(): List<PanelRegistration> {
    return listOf(
      PanelRegistration(R.layout.ui_main) {
        config {
          themeResourceId = R.style.PanelAppThemeTransparent
          includeGlass = false
          enableTransparent = true
        }
      },
      PanelRegistration(R.layout.ui_video) {
        config {
          themeResourceId = R.style.PanelAppThemeTransparent
          includeGlass = false
          enableTransparent = true
        }
        panel {
          val webView = rootView?.findViewById<WebView>(R.id.youtube_video)
          val webSettings = webView!!.settings
          webSettings.javaScriptEnabled = true
          webSettings.mediaPlaybackRequiresUserGesture = false
          webView.loadUrl("https://www.youtube.com/embed/5qqsMjy8Rx0/?rel=0&showinfo=0&autoplay=1&fs=0&loop=1")
        }
      },
      PanelRegistration(R.layout.ui_controls) {
        config {
          themeResourceId = R.style.PanelAppThemeTransparent
          includeGlass = false
          enableTransparent = true
        }
        panel {
          setupUIControls(rootView)
        }
      }
    )
  }

  private fun setupToggleButton(
    view: View?,
    buttonId: Int,
    getState: () -> Boolean,
    setState: (Boolean) -> Unit,
    getText: (Boolean) -> String
  ) {
    val button = view?.findViewById<Button>(buttonId)
    button?.setOnClickListener {
      val newState = !getState()
      setState(newState)
      button.text = getText(newState)
    }
  }

  private fun setupUIControls(view: View?) {
    // toggle mars
    setupToggleButton(
      view,
      R.id.show_mars_button,
      { marsVisibility },
      { newState ->
        marsVisibility = newState
        mars?.setComponent(Visible(marsVisibility))
      },
      { newState -> if (newState) getString(R.string.hide_mars) else getString(R.string.show_mars) }
    )

    // toggle perseverance robot
    setupToggleButton(
      view,
      R.id.show_perseverance_button,
      { perseveranceVisibility },
      { newState ->
        perseveranceVisibility = newState
        perseverance?.setComponent(Visible(perseveranceVisibility))
      },
      { newState -> if (newState) getString(R.string.hide_perseverance) else getString(R.string.show_perseverance) }
    )

    // animate perseverance robot
    perseverance?.setComponent(
      Animated(
        startTime = System.currentTimeMillis(),
        playbackState = PlaybackState.PLAYING,
        playbackType = PlaybackType.LOOP
      )
    )

    // toggle for day time or night time
    setupToggleButton(
      view,
      R.id.toggle_day_mode,
      { nightModeVisibility },
      { newState ->
        nightModeVisibility = newState
        skybox?.setComponents(Material().apply {
          baseTextureAndroidResourceId = if (newState) R.drawable.space else R.drawable.skydome
          unlit = true
        })
      },
      { newState -> if (newState) getString(R.string.set_day_mode) else getString(R.string.set_night_mode) }
    )

    // toggle mixed reality mode
    setupToggleButton(
      view,
      R.id.toggle_mr_mode,
      { mrModeOn },
      { newState ->
        mrModeOn = newState
        systemManager.findSystem<LocomotionSystem>().enableLocomotion(!newState)
        scene.enablePassthrough(newState)
        skybox?.setComponent(Visible(!newState))
        environment?.setComponent(Visible(!newState))
      },
      { newState -> if (newState) getString(R.string.mr_mode_off) else getString(R.string.mr_mode_on) }
    )

  }

  private fun loadGLXF(): Job {
    gltfxEntity = Entity.create()
    return activityScope.launch {
      glXFManager.inflateGLXF(
        Uri.parse("apk:///scenes/Composition.glxf"),
        rootEntity = gltfxEntity!!,
        keyName = "example_key_name"
      )
    }
  }
}
