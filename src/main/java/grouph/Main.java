package grouph;

import grouph.core.Server;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        Server server = new Server(); // -> server.start()

        // this is new to me so ill explain
        // - runtime class lets us interact with jvm itself, in our example, exit the program
        // - we register a new addShutdownHook thread with the JVM, this runs automatically when JVM begins shutdown sequence
        //      - so when the user does ctrl+c
        //      - it calls System.exit()
        // - the hook give the program a chance to clean up, close files, stop threads (server.stop())

        // TLDR, does something just before shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
