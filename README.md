I did code commit, I tested it, seems to work well

I did code stage, I tested it, it also seems to work well

I did code checkout, I tested it, works for checking out commits with just added files, but is not fully functional for checking out commits with
added directories because of inconsistencies between the full relative path listed in the index file, and just the parent/name type path listed in the 
tree files. (this was coded in a previous day, but the formatting was again, incosistent with index so I didnt fully work it out)

^^this was the major bug that I ran into.