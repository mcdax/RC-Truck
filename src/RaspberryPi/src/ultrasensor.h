#include <wiringPi.h>

struct ultrasensor {
	uint8_t pin_ECHO;
	uint8_t pin_TRIGGER;
	volatile int lastCM;
};

int getCM(ultrasensor * usens) {

        //Send trig pulse
        digitalWrite(usens->pin_TRIGGER, HIGH);
        delayMicroseconds(20);
        digitalWrite(usens->pin_TRIGGER, LOW);
 
        //Wait for echo start
        long startTime1 = micros();
        while(digitalRead(usens->pin_ECHO) == LOW){
if((micros() - startTime1) > 20000)
{
printf("Ultrasensor Timeout\n");
usens->lastCM = -1;
return -1;
}
}

//Wait for echo end
        long startTime = micros();
        while(digitalRead(usens->pin_ECHO) == HIGH){
if((micros() - startTime) > 20000)
{
// printf("Ultrasensor Timeout\n");
usens->lastCM = -1;
return -1;
}
}
        long travelTime = micros() - startTime;
 
        //Get distance in cm
        int distance = travelTime / 58;
	usens->lastCM = distance;
        return distance;
}
