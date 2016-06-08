import java.util.*;
import java.io.*;

public class TestStreams {
	public static void main(String[] args) {
		try {
			File file = new File("Test.txt");
			file.createNewFile();
			FileOutputStream fout = new FileOutputStream(file);
			FileInputStream fin = new FileInputStream(file);
			fout.write("Hello World".getBytes());
			int temp = fin.read();
			while(temp != -1) {
				System.out.print((char)temp);
				temp = fin.read();
			}
			System.out.println();
			System.out.println(fin.read());
			System.out.println(fin.read());
			
			fout.write("Second Hello World".getBytes());
			
			temp = fin.read();
			while(temp != -1) {
				System.out.print((char)temp);
				temp = fin.read();
			}
			System.out.println();
		} catch(IOException e) {
			System.out.println("IOException");
		}
	}
}