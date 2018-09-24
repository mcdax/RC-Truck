pthread_t makeSoundThread;

void sendData(int sockfd, char *json)
{
	int n;
	if (write(sockfd, json, strlen(json)) < 0 && errno == EAGAIN)
	{
		if(DEBUG)
		{
			printf("Resource unavailable\n");
		}
	}
}

int getData(int sockfd)
{
	uint8_t data;

	while (read(sockfd, &data, sizeof(uint8_t)) < 0 && errno == EAGAIN)
		;
	if(DEBUG)
	{
		printf("Got: %d\n", data);
	}
	return data;
}

void *blinking(void *vargp)
{

	struct status *currentStatus = (status*) vargp;
	if(DEBUG)
	{
		printf("Start Blinking in Mode: %d\n", currentStatus->mode);
	}
	while (currentStatus->mode == 11)
	{
		digitalWrite(PIN_BLINKER_LEFT, HIGH);
		usleep(BLINKERMS * 1000);
		digitalWrite(PIN_BLINKER_LEFT, LOW);
		usleep(BLINKERMS * 1000);
	}

	while (currentStatus->mode == 12)
	{
		digitalWrite(PIN_BLINKER_RIGHT, HIGH);
		usleep(BLINKERMS * 1000);
		digitalWrite(PIN_BLINKER_RIGHT, LOW);
		usleep(BLINKERMS * 1000);
	}
	if(DEBUG)
	{
		printf("Stop Blinking in Mode: %d\n", currentStatus->mode);
	}
}

void *makeSound(void *vargp)
{

	struct status *currentStatus = (status*) vargp;
	char *name[] =
	{ "back.mp3", "horn.mp3", "caution.mp3", "ridin.mp3" };
	int i = -1;

	if (currentStatus->speed < 0)
	{
		i = 0;
	}
	if (currentStatus->caution)
	{
		i = 2;
	}
	if (currentStatus->horn)
	{
		i = 1;
		currentStatus->horn = false;
		currentStatus->hornPlaying = true;
	}
	if (currentStatus->ridin)
	{
		i = 3;
		currentStatus->ridin = false;
		currentStatus->ridinPlaying = true;
	}
	if (i != -1)
	{

		currentStatus->soundPlaying = true;
		if(DEBUG)
		{
			printf("Start playing of %s\n", name[i]);
		}
		int pid;
		pid=fork();
		if(pid==0)
		{
			execlp("/usr/bin/omxplayer", " ", name[i], NULL);
				currentStatus->ridinPlaying = false;
				currentStatus->hornPlaying = false;
				currentStatus->soundPlaying = false;
			_exit(0);
		}
		else
		{
			wait();
		} 

	}
}

void playSound()
{
	currentStatus->ridinPlaying = false;
	currentStatus->hornPlaying = false;
	currentStatus->soundPlaying = false;

	system("killall omxplayer.bin");
	pthread_join(makeSoundThread, NULL);
	pthread_create(&makeSoundThread, NULL, makeSound, (void *) currentStatus);

}

int action(int a)
{
	int respond;
	if(DEBUG)
	{
		printf("Actionfunction received:  %d\n", a);
	}

	pthread_t blinkingThread;
	if (a < 70)
	{
		switch (a)
		{
			case STRAIGHT:
				digitalWrite(PIN_LEFT, LOW);
				digitalWrite(PIN_RIGHT, LOW);
				pthread_cancel(blinkingThread);
				digitalWrite(PIN_BLINKER_LEFT, LOW);
				digitalWrite(PIN_BLINKER_RIGHT, LOW);
				respond = POSITIVERESPONSE;
				break;
			case LEFT:
				digitalWrite(PIN_RIGHT, LOW);
				digitalWrite(PIN_LEFT, HIGH);
				respond = POSITIVERESPONSE;
				pthread_create(&blinkingThread, NULL, blinking,
						(void *) currentStatus);
				break;
			case RIGHT:
				digitalWrite(PIN_LEFT, LOW);
				digitalWrite(PIN_RIGHT, HIGH);
				respond = POSITIVERESPONSE;
				pthread_create(&blinkingThread, NULL, blinking,
						(void *) currentStatus);
				break;
			case LIGHTS_ON:
				digitalWrite(PIN_FRONTLIGHT, HIGH);
				currentStatus->lightsOn = true;
				break;
			case LIGHTS_OFF:
				digitalWrite(PIN_FRONTLIGHT, LOW);
				currentStatus->lightsOn = false;
				break;
			case HORN:
				currentStatus->horn = true;
				playSound();	
				break;
			case RIDIN_DIRTY:
				currentStatus->ridin = true;
				playSound();	
				break;
			case LIGHTS_AUTO_ON:
				currentStatus->lightsAutoOn = true;
				break;
			case LIGHTS_AUTO_OFF:
				currentStatus->lightsAutoOn = false;
				break;
			case MUSIC_OFF:
				currentStatus->ridinPlaying = false;
				currentStatus->hornPlaying = false;
				currentStatus->soundPlaying = false;
				system("killall omxplayer.bin");
				break;
			default:
				respond = NEGATIVERESPONSE;
				if(DEBUG)
				{
					printf("Doesn't find action for: %d\n", a);
				}

		}

		if (respond == POSITIVERESPONSE)
		{

			pthread_mutex_lock(&modeMutex);
			currentStatus->mode = a;
			pthread_mutex_unlock(&modeMutex);
		}
	} else if (a >= MINSPEEDBACK && a <= MAXSPEEDFORWARD)
	{
		pthread_mutex_lock(&speedMutex);
		currentStatus->speed = a - ((MINSPEEDBACK + MAXSPEEDFORWARD) >> 1);
		pthread_mutex_unlock(&speedMutex);
		respond == POSITIVERESPONSE;
		if(DEBUG)
		{
			printf("Action: New Speed: %d\n", currentStatus->speed);
		}

		if (currentStatus->speed < 0)
		{
			playSound();	
		} else
		{
			currentStatus->soundPlaying = false;
			system("killall omxplayer.bin");
		}

	} else if (a == CLOSESOCKET)
	{
		if(DEBUG)
		{
			printf("Action: Closing Socket\n");
		}
		currentStatus->mode = STRAIGHT;
		currentStatus->lightsOn = false;

		digitalWrite(PIN_BACKWARD, LOW);
		digitalWrite(PIN_FORWARD, LOW);

		pthread_cancel(makeSoundThread);
		digitalWrite(PIN_LEFT, LOW);
		digitalWrite(PIN_RIGHT, LOW);
		pthread_cancel(blinkingThread);
		digitalWrite(PIN_BLINKER_LEFT, LOW);
		digitalWrite(PIN_BLINKER_RIGHT, LOW);
		digitalWrite(PIN_FRONTLIGHT, LOW);
		currentStatus->lightsAutoOn = false;
		currentStatus->speed = 0;
		currentStatus->connected = false;
	} else
	{
		respond == NEGATIVERESPONSE;
	}

	if(DEBUG)
	{
		printf("Action done\n");
	}

	return respond;
}


void *securityCheck(void *vargp)
{
	struct status *currentStatus = (status*) vargp;
	ultrasensor **ultrasensors = currentStatus->ultrasensors;

	int frontCM = ultrasensors[FRONTSENSOR]->lastCM;
	int frontLeftCM = ultrasensors[FRONTLEFTSENSOR]->lastCM;
	int frontRightCM = ultrasensors[FRONTRIGHTSENSOR]->lastCM;
	int backCM = ultrasensors[BACKSENSOR]->lastCM;

	double cautionFactors[] =
	{ CAUTIONFACTOR_1, CAUTIONFACTOR_2, CAUTIONFACTOR_3, CAUTIONFACTOR_4,
		CAUTIONFACTOR_5 };

	int frontCautionCM = CAUTIONFRONT_CM
		* cautionFactors[abs(currentStatus->speed) - 1];
	int frontLeftCautionCM = CAUTIONFRONTLEFT_CM
		* cautionFactors[abs(currentStatus->speed) - 1];
	int frontRightCautionCM = CAUTIONFRONTRIGHT_CM
		* cautionFactors[abs(currentStatus->speed) - 1];
	int backCautionCM = CAUTIONBACK_CM
		* cautionFactors[abs(currentStatus->speed) - 1];

	if (currentStatus->speed != 0 && frontCM != -1 && frontLeftCM != -1 && frontRightCM != -1
			&& backCM != -1)
	{
		currentStatus->caution = false;

		switch (currentStatus->speed)
		{
			case -5:
				if (backCM < backCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Back\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = 5;
					currentStatus->caution = true;
					playSound();
					usleep(350 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}
				}
				break;
			case -4:
				if (backCM < backCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Back\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = 5;
					currentStatus->caution = true;
					playSound();
					usleep(290 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case -3:
				if (backCM < backCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Back\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = 5;
					currentStatus->caution = true;
					playSound();
					usleep(200 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case -2:
				if (backCM < backCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Back\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = 5;
					currentStatus->caution = true;
					playSound();
					usleep(150 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case -1:
				if (backCM < backCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Back\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = 5;
					currentStatus->caution = true;
					playSound();
					usleep(100 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case 1:
				if (frontCM < frontCautionCM || frontLeftCM < frontLeftCautionCM
						|| frontRightCM < frontRightCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Front\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = -5;
					currentStatus->caution = true;
					playSound();
					usleep(100 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case 2:
				if (frontCM < frontCautionCM || frontLeftCM < frontLeftCautionCM
						|| frontRightCM < frontRightCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Front\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = -5;
					currentStatus->caution = true;
					playSound();
					usleep(150 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case 3:
				if (frontCM < frontCautionCM || frontLeftCM < frontLeftCautionCM
						|| frontRightCM < frontRightCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Front\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = -5;
					currentStatus->caution = true;
					playSound();
					usleep(200 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case 4:
				if (frontCM < frontCautionCM || frontLeftCM < frontLeftCautionCM
						|| frontRightCM < frontRightCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Front\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = -5;
					currentStatus->caution = true;
					playSound();
					usleep(290 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
			case 5:
				if (frontCM < frontCautionCM || frontLeftCM < frontLeftCautionCM
						|| frontRightCM < frontRightCautionCM)
				{
					if(DEBUG)
					{
						printf("Detect obstacle: Front\n");
					}
					pthread_mutex_lock(&speedMutex);
					currentStatus->speed = -5;
					currentStatus->caution = true;
					playSound();
					usleep(350 * 1000);
					currentStatus->speed = 0;
					pthread_mutex_unlock(&speedMutex);
					if(DEBUG)
					{
						printf("Emergency Stop succeed\n");
					}

				}
				break;
		}

	}

	if (currentStatus->lightsAutoOn)
	{
		if (digitalRead(PIN_LIGHTSENSOR) == 0 && !currentStatus->lightsOn)
		{
			action(LIGHTS_ON);
		} else if(digitalRead(PIN_LIGHTSENSOR) == 1 && currentStatus->lightsOn)
		{
			action(LIGHTS_OFF);
		}
	}

}

void *receiveCommand(void *vargp)
{
	struct status *currentStatus = (status*) vargp;
	ultrasensor **ultrasensors = currentStatus->ultrasensors;

	while (currentStatus->connected)
	{
		int data = getData(currentStatus->newsockFD);
		int response = action(data);

	}
}

void *speedRegulatedDriving(void *vargp)
{
	struct status *currentStatus = (status*) vargp;

	while (currentStatus->connected)
	{
		while (currentStatus->speed > 0)
		{
			digitalWrite(PIN_FORWARD, HIGH);
			digitalWrite(PIN_BACKWARD, LOW);
			switch (currentStatus->speed)
			{
				case 1:
					usleep(5 * 1000);
					break;
				case 2:
					usleep(5 * 1000);
					break;
				case 3:
					usleep(8 * 1000);
					break;
				case 4:
					usleep(11 * 1000);
					break;
				case 5:
					usleep(0);
					break;
			}

			if (currentStatus->speed != 5)
			{
				digitalWrite(PIN_FORWARD, LOW);

				switch (currentStatus->speed)
				{
					case 1:
						usleep(15 * 1000);
						break;
					case 2:
						usleep(10 * 1000);
						break;
					case 3:
						usleep(10 * 1000);
						break;
					case 4:
						usleep(10 * 1000);
						break;
				}
			}

		}
		while (currentStatus->speed < 0)
		{
			digitalWrite(PIN_FORWARD, LOW);
			digitalWrite(PIN_BACKWARD, HIGH);
			switch (currentStatus->speed)
			{
				case -1:
					usleep(5 * 1000);
					break;
				case -2:
					usleep(5 * 1000);
					break;
				case -3:
					usleep(8 * 1000);
					break;
				case -4:
					usleep(11 * 1000);
					break;
				case -5:
					usleep(0);
					break;
			}

			if (currentStatus->speed != -5)
			{
				digitalWrite(PIN_BACKWARD, LOW);

				switch (currentStatus->speed)
				{
					case -1:
						usleep(15 * 1000);
						break;
					case -2:
						usleep(10 * 1000);
						break;
					case -3:
						usleep(10 * 1000);
						break;
					case -4:
						usleep(10 * 1000);
						break;
				}
			}
		}
		if (currentStatus->speed == 0)
		{
			digitalWrite(PIN_BACKWARD, LOW);
			digitalWrite(PIN_FORWARD, LOW);
		}
	}
}

void *sendCurrentStats(void *vargp)
{
	struct status *currentStatus = (status*) vargp;
	ultrasensor **ultrasensors = currentStatus->ultrasensors;
	cJSON *root, *fmt;

	root = cJSON_CreateObject();
	cJSON_AddItemToObject(root, "distances", fmt = cJSON_CreateObject());
	cJSON_AddNumberToObject(fmt, "front", ultrasensors[FRONTSENSOR]->lastCM);
	cJSON_AddNumberToObject(fmt, "front-right",
			ultrasensors[FRONTRIGHTSENSOR]->lastCM);
	cJSON_AddNumberToObject(fmt, "front-left",
			ultrasensors[FRONTLEFTSENSOR]->lastCM);
	cJSON_AddNumberToObject(fmt, "back", ultrasensors[BACKSENSOR]->lastCM);
	pthread_mutex_lock(&modeMutex);
	cJSON_AddNumberToObject(root, "currentSpeed", currentStatus->speed);
	cJSON_AddNumberToObject(root, "currentMode", currentStatus->mode);
	cJSON_AddNumberToObject(root, "caution", currentStatus->caution);
	cJSON_AddNumberToObject(root, "lights", currentStatus->lightsOn);
	cJSON_AddNumberToObject(root, "lightsautomation",
			currentStatus->lightsAutoOn);
	cJSON_AddNumberToObject(root, "ridinPlaying", currentStatus->ridinPlaying);
	cJSON_AddNumberToObject(root, "hornPlaying", currentStatus->hornPlaying);
	pthread_mutex_unlock(&modeMutex);
	char *rendered = cJSON_Print(root);
	char newStr[strlen(rendered) + 1];
	sprintf(newStr, "%s\n", rendered);
	sendData(currentStatus->newsockFD, newStr);
	cJSON_Delete(root);
	free(rendered);
}

