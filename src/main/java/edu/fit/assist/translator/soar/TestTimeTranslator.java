package edu.fit.assist.translator.soar;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

/**
 * Test entry point that bypasses JSoar loading for direct testing of translation logic
 */
public class TestTimeTranslator {
    public static void main(String[] args) {
        // Create a simple test SoarRules object
        SoarRules testRules = createTestRules();
        
        // Test the time-based translator
        TimeBasedTranslator translator = new TimeBasedTranslator(testRules);
        String output = translator.translateToTimeBased();
        
        System.out.println(output);
        
        // Save to file
        try {
            PrintWriter pw = new PrintWriter("test_output.pm");
            pw.println(output);
            pw.close();
            System.out.println("\n// Output saved to test_output.pm");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    private static SoarRules createTestRules() {
        SoarRules rules = new SoarRules();
        
        // Create initialize rule
        rules.createNewRule("apply*initialize");
        Rule initRule = rules.getRuleByName("apply*initialize");
        initRule.addAttrValue("name", "mission-monitor");
        initRule.addAttrValue("total-time", "1200");
        initRule.addAttrValue("time-counter", "1");
        initRule.addAttrValue("sick", "0");
        initRule.addAttrValue("sick_thres", "0.5");
        initRule.addAttrValue("sickness-checked", "no");
        
        // Create some transition rules
        rules.createNewRule("propose*notsick");
        Rule notsickRule = rules.getRuleByName("propose*notsick");
        notsickRule.addGuard("sickness-checked = no");
        notsickRule.addGuard("name = sickness-monitor");
        
        rules.createNewRule("apply*apply-notsick");
        Rule applyNotsick = rules.getRuleByName("apply*apply-notsick");
        applyNotsick.addAttrValue("sick", "0");
        applyNotsick.addAttrValue("sickness-checked", "yes");
        
        return rules;
    }
}
