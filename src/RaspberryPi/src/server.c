#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <wiringPi.h>
#include <pthread.h>
#include <ao/ao.h>
#include <mpg123.h>
#include "constants.h"
#include "status.h"
#include "ultrasensor.h"
#include "cJSON/cJSON.h"

#define BITS 8

pthread_mutex_t mutex;
pthread_mutex_t modeMutex;
pthread_mutex_t speedMutex;

struct status *currentStatus;

#include "functions.c"


void setup()
{
	currentStatus = (struct status*) malloc(sizeof(struct status));
	currentStatus->mode = STRAIGHT;
	currentStatus->speed = 0;
	currentStatus->connected = false;
	currentStatus->caution = false;
	currentStatus->ridin = false;
	currentStatus->horn = false;
	currentStatus->lightsOn = false;
	currentStatus->lightsAutoOn = true;

	struct ultrasensor **ultrasensors = currentStatus->ultrasensors;

	wiringPiSetup();

	for(int i = 0; i < NUMBEROFULTRASENSORS; i++)
	{
		ultrasensors[i] = (struct ultrasensor*) malloc(sizeof(struct ultrasensor));
	}


	ultrasensors[FRONTRIGHTSENSOR]->pin_ECHO = PIN_ECHO_1;
	ultrasensors[FRONTRIGHTSENSOR]->pin_TRIGGER = PIN_TRIGGER_1;

	ultrasensors[FRONTSENSOR]->pin_ECHO = PIN_ECHO_2;
	ultrasensors[FRONTSENSOR]->pin_TRIGGER = PIN_TRIGGER_2;

	ultrasensors[FRONTLEFTSENSOR]->pin_ECHO = PIN_ECHO_3;
	ultrasensors[FRONTLEFTSENSOR]->pin_TRIGGER = PIN_TRIGGER_3;

	ultrasensors[BACKSENSOR]->pin_ECHO = PIN_ECHO_4;
	ultrasensors[BACKSENSOR]->pin_TRIGGER = PIN_TRIGGER_4;

	for(int i = 0; i < NUMBEROFULTRASENSORS; i++)
	{
		pinMode(ultrasensors[i]->pin_TRIGGER, OUTPUT);
		pinMode(ultrasensors[i]->pin_ECHO, INPUT);
		digitalWrite(ultrasensors[i]->pin_TRIGGER, LOW);
	}

	pinMode(PIN_LEFT, OUTPUT);
	pinMode(PIN_RIGHT, OUTPUT);
	pinMode(PIN_BACKWARD, OUTPUT);
	pinMode(PIN_FORWARD, OUTPUT);
	pinMode(PIN_LIGHTSENSOR, INPUT);
	pinMode(PIN_FRONTLIGHT, OUTPUT);

	pinMode(PIN_BLINKER_LEFT, OUTPUT);
	pinMode(PIN_BLINKER_RIGHT, OUTPUT);

	digitalWrite(PIN_LEFT, LOW);
	digitalWrite(PIN_RIGHT, LOW);
	digitalWrite(PIN_FORWARD, LOW);
	digitalWrite(PIN_BACKWARD, LOW);
	digitalWrite(PIN_FRONTLIGHT, LOW);
	digitalWrite(PIN_BLINKER_LEFT, LOW);
	digitalWrite(PIN_BLINKER_RIGHT, LOW);

	delay(30);
}

void error( char *msg ) {
	perror(  msg );
	exit(1);
}

int main(int argc, char *argv[]) {
	setup();

	pthread_t receiveCommandThread;
	pthread_t sendCurrentStatsThread;
	pthread_t speedRegulatedDrivingThread;


	pthread_mutex_init (&mutex, NULL);
	pthread_mutex_init (&modeMutex, NULL);
	pthread_mutex_init (&speedMutex, NULL);


	int sockfd, newsockfd, portno = 1337, clilen;
	char buffer[256];
	struct sockaddr_in serv_addr, cli_addr;


	printf( "using port #%d\n", portno );

	sockfd = socket(AF_INET, SOCK_STREAM, 0);
	if (sockfd < 0)
		error( const_cast<char *>("ERROR opening socket") );
	bzero((char *) &serv_addr, sizeof(serv_addr));

	serv_addr.sin_family = AF_INET;
	serv_addr.sin_addr.s_addr = INADDR_ANY;
	serv_addr.sin_port = htons( portno );
	if (bind(sockfd, (struct sockaddr *) &serv_addr,
				sizeof(serv_addr)) < 0)
		error( const_cast<char *>( "ERROR on binding" ) );
	listen(sockfd,5);
	clilen = sizeof(cli_addr);

	//--- infinite wait on a connection ---
	while ( 1 ) {
		printf( "waiting for new client...\n" );
		if ( ( newsockfd = accept4( sockfd, (struct sockaddr *) &cli_addr, (socklen_t*) &clilen, SOCK_NONBLOCK) ) < 0 )
			error( const_cast<char *>("ERROR on accept") );
		printf( "opened new communication with client\n" );


		currentStatus->newsockFD = newsockfd;
		currentStatus->connected = true;		
		ultrasensor **ultrasensors = currentStatus->ultrasensors;

		pthread_create(&receiveCommandThread, NULL, receiveCommand,(void *) currentStatus);
		pthread_create(&speedRegulatedDrivingThread, NULL, speedRegulatedDriving,(void *) currentStatus);

		digitalWrite(PIN_BLINKER_RIGHT, HIGH);
		digitalWrite(PIN_BLINKER_LEFT, HIGH);
		usleep(1000 * 1000);
		digitalWrite(PIN_BLINKER_RIGHT, LOW);
		digitalWrite(PIN_BLINKER_LEFT, LOW);


		while(currentStatus->connected)
		{

			for(int i = 0; i < NUMBEROFULTRASENSORS; i++)
			{
				getCM(ultrasensors[i]);
			}
			pthread_create(&sendCurrentStatsThread, NULL, sendCurrentStats, (void *) currentStatus);
			securityCheck((void *) currentStatus);
			pthread_join(sendCurrentStatsThread, NULL);
			usleep(REFRESHMS*1000);

		}
		close( newsockfd );

		currentStatus->newsockFD = 0;
	}
	printf("END");
	return 0;
}

