package logger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object SystemLogger {
	val data: MutableList<WindowInfo> = mutableListOf()
	private var activeWindow: WindowInfo? = null
	
	lateinit var isRunning: StateFlow<Boolean>
	val scope = CoroutineScope(Dispatchers.IO)
	
	fun start() {
		// The loggers are all started in coroutines
		KeyboardLogger.start()
		MouseLogger.start()
		WindowLogger.start()
		
		// Start elaborating the data and emitting it
		elaborateData()
		
		// Connect the isRunning of each logger to the isRunning of the SystemLogger
		scope.launch {
			isRunning = combine(
				KeyboardLogger.isRunning,
				MouseLogger.isRunning,
				WindowLogger.isRunning
			) { k, m, w -> k && m && w }.stateIn(CoroutineScope(Dispatchers.Default))
		}
	}
	
	fun stop() {
		KeyboardLogger.stop()
		MouseLogger.stop()
		WindowLogger.stop()
	}
	
	fun saveData() {
		// Save the data to the database
		data.forEach { it.save() }
	}
	
	private fun elaborateData() {
		// Increase the key press count of the key in the active window
		scope.launch {
			KeyboardLogger.keyPresses.collect { kc ->
				getWindowInfo(WindowLogger.activeWindow)?.addKeyPress(kc)
			}
		}
		// Handle the mouse events
		scope.launch {
			MouseLogger.mouseEvents.collect { mouseEvent ->
				// Handle the mouseEvent here
				getWindowInfo(WindowLogger.activeWindow)?.let {
					// Add the mouse event to the window info
					when (mouseEvent) {
						is ButtonPressEvent -> it.addMouseButtonPress(mouseEvent.button)
						is ScrollWheelEvent -> it.addScrollWheelEvent(mouseEvent.direction)
						is MovementEvent -> it.addMouseMovement(mouseEvent.position)
					}
				}
			}
		}
		// Increase the scroll direction count of the direction in the active window
		scope.launch {
			WindowLogger.activeWindows.collect { window ->
				// Check if the window is already in the data
				val windowInfo = getWindowInfo(window.name)
				if (windowInfo != null) {
					// Focus the window
					windowFocused(windowInfo)
				} else {
					// Create a new window info
					val newWindowInfo = WindowInfo(window.name)
					// Focus the window
					windowFocused(newWindowInfo)
					// Add the new window info to the data
					data += newWindowInfo
				}
			}
		}
	}
	
	fun getWindowInfo(windowName: String): WindowInfo? {
		return data.find { it.id == windowName }
	}
	
	private fun windowFocused(newActiveWindow: WindowInfo) {
		// Check if the window is already the active window
		if (newActiveWindow == activeWindow) return
		
		// Focus the new window
		newActiveWindow.windowFocused()
		// Unfocus the previous window
		activeWindow?.windowUnfocused()
		// Update the active window
		activeWindow = newActiveWindow
	}
}