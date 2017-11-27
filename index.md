The blockchain synchronization task

The blockchain consists of blocks. 
Suppose for this task that the blocks do not carry any useful information and simply increase the total chain score. 
The block is valid in the following case:

* block score is a positive number
* block id is a valid uuid
* block parent id is an id of last block of the blockchain or there are no last block (for genesis block)

It is stored by network of nodes that exchange the blocks of best chain with each other.
The best is the chain with the highest total score of its blocks. 
A node at startup may not have a chain and other connected nodes. 

The first block of the chain (genesis block) is valid in the following case:

* block is valid by rules above
* there are no other blocks in blockchain

Nodes should be synchronized if only they have the same genesis block! 
If the genesses are different, then it is considered that they are nodes of different networks and they should not be interconnected.
Suppose for this task that if a node does not have a genesis block, then it can add any valid one.

A blockchain node should know the list of all nodes connected to it (both incoming and outgoing connections). 
Once the node gets the continuation of the best chain, it must validate it and pass to its neighbors.
Suppose for this task that the nodes do not exchange peers.

The purpose of the task is to synchronize the blocks of best chain between the nodes of the network using messages with a minimum amount of transmitted data.
You can not simply transfer the entire blockchain to another node on the network, because usually this is a very large amount of data.