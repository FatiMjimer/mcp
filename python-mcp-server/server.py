from mcp.server.fastmcp import FastMCP

mcp = FastMCP('Python-MCP-Server')
@mcp.tool()
def get_info_about(name : str) -> str:
    return {
        "first_name" : name,
        "last_name" : "Mohamed",
        "salary":5400,
        "email":"med@gmail.com"
    }