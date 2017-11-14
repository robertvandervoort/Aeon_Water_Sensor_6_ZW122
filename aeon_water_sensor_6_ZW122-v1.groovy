metadata {
  definition (name: "Aeon Water Sensor 6", namespace: "robertvandervoort", author: "Robert Vandervoort", ocfDeviceType: "x.com.st.d.sensor.moisture") {
    capability "Battery"
    capability "Power Source"
    capability "Configuration"
    capability "Notification"
    capability "Sensor"
    capability "Switch"
    capability "Shock Sensor"
    capability "Temperature Measurement"
    capability "Water Sensor"
    capability "Zw Multichannel"
    
	attribute "switch1", "enum", ["on", "off"]
	attribute "switch2", "enum", ["on", "off"]
    
	command "configure"
    command "getTemp"
    command "getPosition"
    command "getBattery"
    command "clearShock"
/*
type:0701 mfr:0086 prod:0102 model:007A
cc:5E,86,84,98,72,5A sec:85,59,80,70,7A,71,73,31,60,8E
*/
    fingerprint mfr: "0086", prod: "0102", model: "007A"
  }

  simulator {
    status "dry": "command: 3003, payload: 00"
    status "wet": "command: 3003, payload: FF"
  }
  
  tiles (scale: 2){
    multiAttributeTile(name:"water", type: "generic", width: 6, height: 4, canChangeIcon: true){
      tileAttribute("device.water", key: "PRIMARY_CONTROL") {
        attributeState("dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff")
        attributeState("wet", icon:"st.alarm.water.wet", backgroundColor:"#00A0DC")
      }
      tileAttribute("device.probe", key: "SECONDARY_CONTROL") {
          attributeState("1", label: "probe 1", icon: "st.switches.switch.on", backgroundColor: "#79b821")
	      attributeState("2", label: "probe 2", icon: "st.switches.switch.off", backgroundColor: "#ffffff")
          attributeState("dry", label: "dry")
	  }
    }
	valueTile("temperature","device.temperature", width: 2, height: 2) {
      state "temperature",label:'${currentValue}', action:"getTemp", precision:2, backgroundColors:[
          [value: 32, color: "#153591"],
          [value: 44, color: "#1e9cbb"],
          [value: 59, color: "#90d2a7"],
          [value: 74, color: "#44b621"],
          [value: 84, color: "#f1d801"],
          [value: 92, color: "#d04e00"],
          [value: 98, color: "#bc2323"]
      ]
    }
    valueTile("powerSource","device.powerSource", decoration: "flat", width: 3, height: 2) {
      state "powerSource", label:'${currentValue} powered'
    }    
    valueTile("battery", "device.battery", width: 3, height: 2, decoration: "flat") {
      state "battery", label:'${currentValue}% battery', unit:"", action: "getBattery"
    }
    valueTile("position","device.position", decoration: "flat", width: 4, height: 2) {
      state "position", label:'Mounted ${currentValue}', action: "getPosition"
    }
    standardTile("shockSensor","device.shockSensor", decoration: "flat", width: 2, height: 2) {
	  state("clear", label:'clear', icon:"st.contact.contact.closed", backgroundColor:"#cccccc")
      state("detected", label:'shock', action: "clearShock", icon:"st.contact.contact.open", backgroundColor:"#e86d13")
    }	
    standardTile("configure","device.configure", decoration: "flat", width: 3, height: 2) {
      state "configure", label:'config', action:"configure", icon:"st.secondary.tools"
    }
    main (["water"])
    details(["water","temperature","position","powerSource","battery","shockSensor","configure"])
  }
  preferences {
    input "debugOutput", "boolean",
        title: "Enable debug logging?",
        defaultValue: false,
        displayDuringSetup: true
  }
}

def updated()
{
  updateDataValue("configured", "false")
  state.debug = ("true" == debugOutput)
  if (state.sec && !isConfigured()) {
    // in case we miss the SCSR
    response(configure())
    setConfigured()
  }
}

def parse(String description)
{
  def result = null
  if (description.startsWith("Err 106")) {
    state.sec = 0
    result = createEvent( name: "secureInclusion", value: "failed", isStateChange: true,
        descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.")
  } else if (description != "updated") {
    def cmd = zwave.parse(description, [0x31: 5, 0x30: 2, 0x7A: 2, 0x71: 3, 0x84: 1, 0x86: 1])
    if (cmd) {
      result = zwaveEvent(cmd)
    }
  }
  if (state.debug) log.debug "Parsed '${description}' to ${result.inspect()}"
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand([0x31: 5, 0x30: 2, 0x7A: 2, 0x84: 1, 0x86: 1])
  state.sec = 1
  //if (state.debug) log.debug "encapsulated: ${encapsulatedCommand}"
  if (encapsulatedCommand) {
    zwaveEvent(encapsulatedCommand)
  } else {
    log.warn "Unable to extract encapsulated cmd from $cmd"
    createEvent(descriptionText: cmd.toString())
  }
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
  response(configure())
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionCommandClassReport cmd) {
  if (state.debug) log.debug "---COMMAND CLASS VERSION REPORT V1--- \r\n ${device.displayName} has command class version: ${cmd.commandClassVersion} - payload: ${cmd.payload}"
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
  log.debug("ConfigurationReportv1 ${cmd.inspect()}")
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {  
  def event = [];
  
  switch (cmd.parameterNumber) {
  	case 9:
  	  if (cmd.scaledConfigurationValue == 2) {
        event << createEvent(name: "powerSource", value: "DC", displayed: true, isStateChange: true, descriptionText: "${device.displayName} is connected to power via USB.");
      } else {
        event << createEvent(name: "powerSource", value: "battery", displayed: true, isStateChange: true, descriptionText: "${device.displayName} is running on battery power.");
      }
	  break;
	case 84:
   	  if (cmd.scaledConfigurationValue == 0) {
        event << createEvent(name: "position", value: "horizontal", displayed: true, isStateChange: true, descriptionText: "${device.displayName} is mounted horizontally.");
      } else if (cmd.scaledConfigurationValue == 1) {
        event << createEvent(name: "position", value: "vertical", displayed: true, isStateChange: true, descriptionText: "${device.displayName} is mounted vertically.");
	  }
      break;
	case 136:
      if (cmd.scaledConfigurationValue == 1) {
        event << createEvent(name: "probe", value: "1", displayed: true, isStateChange: true, descriptionText: "${device.displayName} detected water on probe 1.");
      } else if (cmd.scaledConfigurationValue == 2) {
        event << createEvent(name: "probe", value: "2", displayed: true, isStateChange: true, descriptionText: "${device.displayName} detected water on probe 2.");
	  }
      break;
    default:
      if (state.debug) log.debug log.debug("ConfigurationReportv2 ${cmd.inspect()}");
      break;
  }
  return event;
}

def zwaveEvent(physicalgraph.zwave.commands.powerlevelv1.PowerlevelTestNodeReport cmd) {
  if(state.debug) log.debug "===Power level test node report received=== \r\n ${device.displayName}: statusOfOperation: ${cmd.statusOfOperation} testFrameCount: ${cmd.testFrameCount} testNodeid: ${cmd.testNodeid}"
  def result = []
  result << response(zwave.commands.powerlevelv1.PowerlevelGet())
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
  def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
  updateDataValue("fw", fw)
  if(state.debug) log.debug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
  if(state.debug) log.debug "---FIRMWARE MD REPORT V1--- \r\n ${device.displayName} reports \r\n checksum:  ${cmd.checksum} \r\n firmwareId:  ${cmd.firmwareId} \r\n manufacturerId:  ${cmd.manufacturerId}"
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  def result = []
  if(state.debug) {
  	log.debug "manufacturerId:   ${cmd.manufacturerId}"
    log.debug "manufacturerName: ${cmd.manufacturerName}"
    log.debug "productId:        ${cmd.productId}"
    log.debug "productTypeId:    ${cmd.productTypeId}"
  }
  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)
  result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false, displayed: true)
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
  sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
  sensorValueEvent(cmd.sensorState)
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
  def result = []
  
  switch(cmd.notificationType) {
  case 0x05:
    if (cmd.event == 0x00) {
      result << createEvent(descriptionText: "$device.displayName is dry.", name: "water", value: "dry", displayed: true, isStateChange: true);
      result << createEvent(name: "probe", value: "dry", displayed: false, isStateChange: true);
    }
    if (cmd.event == 0x02) {
      result << createEvent(descriptionText: "$device.displayName is wet.", name: "water", value: "wet", displayed: true, isStateChange: true)
      result << response(queryProbes())
    }
    break
  case 0x04:
    if (cmd.event == 0x00) {
      result << createEvent(descriptionText: "$device.displayName temperature normalized", isStateChange: true)
    } else if (cmd.event <= 0x02) {
      result << createEvent(descriptionText: "$device.displayName detected overheat", isStateChange: true)
    } else if (cmd.event == 0x06) {
      result << createEvent(descriptionText: "$device.displayName detected low temperature", isStateChange: true)
    }
    break
  case 0x07:
    if (cmd.event == 0x00) {
      result << createEvent(name: "shockSensor", value: "clear", descriptionText: "$device.displayName shock cleared", isStateChange: true)
    } else if (cmd.event == 0x03) {
      result << createEvent(name: "shockSensor", value: "detected", descriptionText: "$device.displayName shock detected", isStateChange: true)
      result << response(getPosition())
      //have to add this clear event because the sensor isn't sending a clear event.
      //result << createEvent(name: "shockSensor", value: "clear", descriptionText: "$device.displayName shock cleared", isStateChange: true)
    }
    break
  default:
    if(state.debug) log.debug "notification event received : ${inspect(cmd)}";
    break;
  }
  
  return result;
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) {
  def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]
      if (!isConfigured()) {
    // we're still in the process of configuring a newly joined device
    if (state.debug) log.debug("late configure")
    result += response(configure())
  }

  if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
    result << response(zwave.batteryV1.batteryGet())
  }

  result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
  def result = []
  if (cmd.batteryLevel == 0xFF || cmd.batteryLevel < 11 ) {
    result << createEvent(name: "battery", unit: "%", displayed: "true", value: 1, descriptionText: "${device.displayName} has a low battery", isStateChange: true)
  } else {
    result << createEvent(name: "battery", unit: "%", displayed: "true", value: cmd.batteryLevel, descriptionText: "${device.displayName} battery is ${cmd.batteryLevel}", isStateChange: true)
  }
  state.lastbat = new Date().time
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
  def map = [ displayed: true, value: cmd.scaledSensorValue.toString() ]
  switch (cmd.sensorType) {
    case 1:
      map.name = "temperature"
      def cmdScale = cmd.scale == 1 ? "F" : "C"
      map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
      map.unit = getTemperatureScale()
      break;
  }
  createEvent(map)
}

// parse the unhandled
def zwaveEvent(physicalgraph.zwave.Command cmd) {
  log.debug "Unhandled: $cmd"
  createEvent(descriptionText: cmd.toString(), isStateChange: false)
}

//commands
def getPosition() {
    if (state.debug) log.debug "getPosition pressed \r\n"
  def request = [
    zwave.configurationV1.configurationGet(parameterNumber: 0x54)
  ]
  commands(request)
}

def queryProbes() {
  if (state.debug) log.debug "queryProbe pressed \r\n"
  def request = [
    zwave.configurationV1.configurationGet(parameterNumber: 0x88)
  ]
  commands(request)
}

def getTemp() {
    if (state.debug) log.debug "getTemp pressed \r\n"
  def request = [
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1)
  ]
  commands(request)
}

def getBattery() {
  if (state.debug) log.debug "getBattery pressed \r\n"
  def request = [
    zwave.batteryV1.batteryGet()
  ]
  commands(request)
}

def clearShock() {
  if (state.debug) log.debug "clearShock pressed \r\b"
  sendEvent(name: "shockSensor", value: "clear", descriptionText: "$device.displayName shock cleared", isStateChange: true)
}

def configure() {
  if (state.debug) log.debug "Sending configure..."
  def request = [
    // Set the wakeup interval to 4 hours
    zwave.wakeUpV1.wakeUpIntervalSet(seconds:4*3600, nodeid:zwaveHubNodeId),

    // Can use the zwaveHubNodeId variable to add the hub to the device's associations:
    zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
    zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId),
    zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId),
    zwave.associationV1.associationSet(groupingIdentifier:4, nodeId:zwaveHubNodeId),

    // Get version and firmware info
    zwave.versionV1.versionGet(),
    zwave.firmwareUpdateMdV2.firmwareMdGet(),

    // grab battery and temperature values
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1),
    zwave.batteryV1.batteryGet(),
/*
    // get all the config values
    zwave.configurationV1.configurationGet(parameterNumber: 0x02),
    zwave.configurationV1.configurationGet(parameterNumber: 0x08),
    zwave.configurationV1.configurationGet(parameterNumber: 0x09),
    zwave.configurationV1.configurationGet(parameterNumber: 0x0A),
    zwave.configurationV1.configurationGet(parameterNumber: 0x30),
    zwave.configurationV1.configurationGet(parameterNumber: 0x31),
    zwave.configurationV1.configurationGet(parameterNumber: 0x32),
    zwave.configurationV1.configurationGet(parameterNumber: 0x39),
    zwave.configurationV1.configurationGet(parameterNumber: 0x40),
    zwave.configurationV1.configurationGet(parameterNumber: 0x54),
    zwave.configurationV1.configurationGet(parameterNumber: 0x56),
    zwave.configurationV1.configurationGet(parameterNumber: 0x57),
    zwave.configurationV1.configurationGet(parameterNumber: 0x58),
    zwave.configurationV1.configurationGet(parameterNumber: 0x59),
    zwave.configurationV1.configurationGet(parameterNumber: 0x5E),
    zwave.configurationV1.configurationGet(parameterNumber: 0x65),
    zwave.configurationV1.configurationGet(parameterNumber: 0x6F),
    zwave.configurationV1.configurationGet(parameterNumber: 0x87),
    zwave.configurationV1.configurationGet(parameterNumber: 0x88),
    zwave.configurationV1.configurationGet(parameterNumber: 0xC9),
    zwave.configurationV1.configurationGet(parameterNumber: 0xFC),
*/
    // set some decent defaults

    // wakeup after power restoral
    zwave.configurationV1.configurationSet(parameterNumber: 0x02, scaledConfigurationValue: 0, size: 1),

    // timeout to go into the sleep state after the Wake Up Notification
    zwave.configurationV1.configurationSet(parameterNumber: 0x08, scaledConfigurationValue: 60, size:1 ),

    /* Alarm time for the Buzzer when the sensor is triggered
    Value 1: the time of Buzzer keeping OFF state (MSB)
    Value 2: the time of Buzzer keeping OFF state (LSB)
    Value 3: the time of Buzzer keeping ON state (MSB)
    Value 4: repeated cycle of Buzzer alarm.
    Note: one cycle is equal to the Buzzer from ON state to OFF state. 			*/
    zwave.configurationV1.configurationSet(parameterNumber: 0x0A, scaledConfigurationValue: 0|30|10|10, size: 4),

    // Set the low battery value.
    zwave.configurationV1.configurationSet(parameterNumber: 0x27, scaledConfigurationValue: 20, size: 1),

    // Enable/disable the sensor report
    zwave.configurationV1.configurationSet(parameterNumber: 0x30, scaledConfigurationValue: 55, size: 1),

	// enable or disable the buzzer
	zwave.configurationV1.configurationSet(parameterNumber: 0x56, scaledConfigurationValue: 1, size: 1),
    
    // what makes it beep
    zwave.configurationV1.configurationSet(parameterNumber: 0x57, scaledConfigurationValue: 55, size: 1),

    // send basic set FF to group 3 - param 58 when sensor 1 triggered, group 4 - param 59 when sensor 2 is triggered
    // turning these off as we are using the notification command class, though you CAN use these or directly control devices on group 3 or 4 with them, outside of the hub.
    zwave.configurationV1.configurationSet(parameterNumber: 0x58, scaledConfigurationValue: 0, size: 1),
    zwave.configurationV1.configurationSet(parameterNumber: 0x59, scaledConfigurationValue: 0, size: 1),

    // send battery power level reports instead of USB
    zwave.configurationV1.configurationSet(parameterNumber: 0x5E, scaledConfigurationValue: 1, size: 1),

    /* which reports to send unsolicited
      0 = Send Nothing.
      1 = Battery Report.
      2 = Multilevel sensor report for temperature.
      3 = Battery Report and Multilevel sensor report for temperature. */
    zwave.configurationV1.configurationSet(parameterNumber: 0x65, scaledConfigurationValue: 3, size: 1),

    // Set the interval time for sending the unsolicited report
    zwave.configurationV1.configurationSet(parameterNumber: 0x6F, scaledConfigurationValue: 3600, size: 4),

    /* To set which sensor report can be sent when the water leak event is
      triggered and if the receiving device is a non-multichannel device.
      0 = Send nothing.
      1 = Send notification report to association group 1.
      2 = Send configuration 0x88 report to association group 2.
      3 = Send notification report to association group 1 and configuration 0x88 report to association group 2. */
    zwave.configurationV1.configurationSet(parameterNumber: 0x87, scaledConfigurationValue: 0, size: 1)
  ]
  commands(request) + ["delay 5000", zwave.wakeUpV1.wakeUpNoMoreInformation().format()]
}

def sensorValueEvent(Short value) {
  def eventValue = null
  if (value == 0x00) {eventValue = "dry"}
  if (value == 0xFF) {eventValue = "wet"}
  def result = createEvent(name: "water", value: eventValue, displayed: true, isStateChange: true, descriptionText: "$device.displayName is $eventValue")
  return result
}

def enableEpEvents() {
  log.debug "Executing 'enableEpEvents'"
  // TODO: handle 'enableEpEvents' command
}

def epCmd() {
  log.debug "Executing 'epCmd'"
  // TODO: handle 'epCmd' command
}

private setConfigured() {
  updateDataValue("configured", "true")
}

private isConfigured() {
  getDataValue("configured") == "true"
}

private command(physicalgraph.zwave.Command cmd) {
  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private commands(commands, delay=1000) {
  delayBetween(commands.collect{ command(it) }, delay)
}
