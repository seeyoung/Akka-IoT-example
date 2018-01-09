package com.lightbend.akka.sample;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestUtils {
    public static void assertEqualTemperatures(Map<String, DeviceGroup.TemperatureReading> expectedTemperatures, Map<String, DeviceGroup.TemperatureReading> temperatures) {
        temperatures.forEach((k,v)->{
            DeviceGroup.TemperatureReading expectTemp = expectedTemperatures.get(k);
            assertTrue(v.getClass().equals(expectTemp.getClass()));
            if(v instanceof DeviceGroup.Temperature){
                assertEquals(((DeviceGroup.Temperature)v).value, ((DeviceGroup.Temperature)expectedTemperatures.get(k)).value, 0.01);
            }
        });
    }
}
