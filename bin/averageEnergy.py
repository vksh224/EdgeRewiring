#!/usr/bin/python

# Open a file

with open("/Users/vijay/Documents/workspace/ONE/src/default_energy.txt") as f:
#with open("default_settings.txt") as f:

    content = f.readlines()
totalEnergy = 0
numOfNodes = 0
averageEnergy = 0
for line in content:
	words = line.lower().split()
	totalEnergy = totalEnergy + float(words[2])
	numOfNodes = numOfNodes + 1
averageEnergy = totalEnergy/numOfNodes
print numOfNodes 
print averageEnergy




	
	

